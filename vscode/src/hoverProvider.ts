import * as vscode from 'vscode';
import { MoLangSchemaService, SchemaFunction } from './schema';

const CHAIN_PATTERN = /^(q|query|v|variable|t|temp|f|function|c|context|math)((?:\.[a-zA-Z_][a-zA-Z0-9_]*)+)$/;

const KEYWORD_DOCS: Record<string, string> = {
    fn: "`fn('name', (params) -> { body })`\n\nDefines a named function that can be called with `f.name()`.",
    'if': '`if (condition) { then } else { otherwise }`\n\nConditional execution. Returns the value of the executed branch.',
    'else': 'Part of an `if/else` statement.',
    'switch': '`switch(value, case1, { result1 }, case2, { result2 }, { default })`\n\nPattern matching on a value.',
    'while': '`while(condition, { body })`\n\nLoop that executes body while condition is truthy.',
    struct: '`struct()`\n\nCreates a structured data object.',
    import: "`import('namespace:path')`\n\nImports a MoLang script from `data/{namespace}/molang/{path}.molang`.",
    'return': 'Returns a value from the current function or script.',
    'break': 'Breaks out of the current loop.',
    'continue': 'Skips to the next iteration of the current loop.',
    'for': '`for (init; condition; step) { body }`\n\nLoop with initialization, condition, and step.',
    'default': 'Default case in a switch statement.',
};

export class MoLangHoverProvider implements vscode.HoverProvider {
    constructor(private schema: MoLangSchemaService) {}

    provideHover(
        document: vscode.TextDocument,
        position: vscode.Position,
        _token: vscode.CancellationToken
    ): vscode.Hover | undefined {
        if (!this.schema.isLoaded()) return undefined;

        const fullText = document.getText();
        const offset = document.offsetAt(position);

        // Extract the full dot-chain at this position
        const chain = this.extractChainAt(fullText, offset);
        if (!chain) {
            // Check keyword
            const wordRange = document.getWordRangeAtPosition(position);
            if (wordRange) {
                const word = document.getText(wordRange);
                if (word in KEYWORD_DOCS) {
                    return new vscode.Hover(new vscode.MarkdownString(KEYWORD_DOCS[word]));
                }
            }
            return undefined;
        }

        const m = CHAIN_PATTERN.exec(chain);
        if (!m) return undefined;

        const prefix = normalizePrefix(m[1]);
        const dotPart = m[2];
        const parts = dotPart.substring(1).split('.');

        // Infer runtime
        let runtimeName = this.schema.inferRuntimeFromContent(fullText);
        if (!runtimeName) {
            runtimeName = this.schema.inferRuntimeFromPath(document.uri.fsPath);
        }

        // Handle math.xxx
        if (prefix === 'math') {
            if (parts.length >= 1) {
                const mathFuncs = this.schema.getMathFunctions();
                const func = mathFuncs[parts[0]];
                if (func) {
                    return new vscode.Hover(this.buildFunctionDoc('math.' + parts[0], func));
                }
            }
            return undefined;
        }

        // Handle q.xxx.yyy
        if (prefix === 'q') {
            const resolved = this.schema.resolveFunction(runtimeName, parts);
            if (resolved) {
                return new vscode.Hover(this.buildFunctionDoc('q.' + parts.join('.'), resolved));
            }

            // Try as query variable
            if (parts.length === 1) {
                const queryVars = this.schema.getQueryVariables(runtimeName);
                const qv = queryVars[parts[0]];
                if (qv) {
                    return new vscode.Hover(this.buildFunctionDoc('q.' + parts[0], qv));
                }
            }
        }

        return undefined;
    }

    private extractChainAt(text: string, offset: number): string | null {
        let start = offset;
        while (start > 0) {
            const c = text.charAt(start - 1);
            if (/[a-zA-Z0-9_.]/.test(c)) {
                start--;
            } else {
                break;
            }
        }

        let end = offset;
        while (end < text.length) {
            const c = text.charAt(end);
            if (/[a-zA-Z0-9_]/.test(c)) {
                end++;
            } else {
                break;
            }
        }

        if (start >= end) return null;
        const chain = text.substring(start, end);
        if (!chain.includes('.')) return null;
        return chain;
    }

    private buildFunctionDoc(fullName: string, func: SchemaFunction): vscode.MarkdownString {
        const md = new vscode.MarkdownString();
        const returnType = func.returns ?? func.type ?? 'Unknown';
        const paramSig = buildParamSignature(func);

        if (paramSig) {
            md.appendCodeblock(`${fullName}(${paramSig}) → ${returnType}`, 'molang');
        } else {
            md.appendCodeblock(`${fullName} → ${returnType}`, 'molang');
        }

        if (func.description) {
            md.appendMarkdown('\n\n' + func.description);
        }

        if (func.source) {
            md.appendMarkdown('\n\n*Source: ' + func.source + '*');
        }

        if (func.struct_type) {
            md.appendMarkdown('\n\n*Struct type: `' + func.struct_type + '`*');
        }

        // Parameter details
        if (func.params && func.params.length > 0) {
            md.appendMarkdown('\n\n**Parameters:**\n');
            for (const p of func.params) {
                const name = p.name ?? '?';
                const type = p.type ?? '';
                const opt = p.optional ? ' *(optional)*' : '';
                const desc = p.description ? ' — ' + p.description : '';
                md.appendMarkdown(`\n- \`${name}\`: ${type}${opt}${desc}`);
            }
        }

        return md;
    }
}

function normalizePrefix(raw: string): string {
    switch (raw) {
        case 'query': return 'q';
        case 'variable': return 'v';
        case 'temp': return 't';
        case 'function': return 'f';
        case 'context': return 'c';
        default: return raw;
    }
}

function buildParamSignature(func: SchemaFunction): string {
    if (!func.params || func.params.length === 0) return '';
    return func.params.map(p => {
        let s = p.name ?? '?';
        if (p.type) s += ': ' + p.type;
        if (p.optional) s += '?';
        return s;
    }).join(', ');
}

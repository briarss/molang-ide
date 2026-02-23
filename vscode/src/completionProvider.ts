import * as vscode from 'vscode';
import { MoLangSchemaService, SchemaFunction, SchemaParam } from './schema';

const PREFIX_CHAIN_PATTERN = /(?:^|[^a-zA-Z0-9_])(q|query|v|variable|t|temp|f|function|c|context|math)((?:\.[a-zA-Z_][a-zA-Z0-9_]*)*)\.$/;
const CONTEXT_ANNOTATION_PATTERN = /\/\/\s*@context\s+(\S*)$/;

export class MoLangCompletionProvider implements vscode.CompletionItemProvider {
    constructor(private schema: MoLangSchemaService) {}

    provideCompletionItems(
        document: vscode.TextDocument,
        position: vscode.Position,
        _token: vscode.CancellationToken,
        _context: vscode.CompletionContext
    ): vscode.CompletionItem[] | undefined {
        if (!this.schema.isLoaded()) return undefined;

        const lineText = document.lineAt(position).text;
        const textBefore = lineText.substring(0, position.character);

        // Detect runtime context
        const docText = document.getText();
        let runtimeName = this.schema.inferRuntimeFromContent(docText);
        if (!runtimeName) {
            runtimeName = this.schema.inferRuntimeFromPath(document.uri.fsPath);
        }

        // Try // @context annotation completion
        const ctxMatch = CONTEXT_ANNOTATION_PATTERN.exec(textBefore);
        if (ctxMatch) {
            return this.schema.getRuntimeNames().map(name => {
                const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Constant);
                item.detail = 'runtime context';
                item.sortText = '0' + name;
                return item;
            });
        }

        // Try prefix chain completion
        const chainMatch = PREFIX_CHAIN_PATTERN.exec(textBefore);
        if (chainMatch) {
            const prefix = normalizePrefix(chainMatch[1]);
            const chainStr = chainMatch[2];
            const chain = chainStr ? chainStr.substring(1).split('.') : [];
            return this.handleChainCompletion(prefix, chain, runtimeName, document);
        }

        // Bare identifier completion (keywords + prefixes)
        return this.handleBareCompletion();
    }

    private handleChainCompletion(
        prefix: string,
        chain: string[],
        runtimeName: string | null,
        document: vscode.TextDocument
    ): vscode.CompletionItem[] {
        switch (prefix) {
            case 'q': return this.handleQueryCompletion(chain, runtimeName);
            case 'math': return this.handleMathCompletion(chain);
            case 't': return this.handleTempCompletion(document);
            case 'v': return this.handleVariableCompletion(document);
            case 'f': return this.handleFunctionCompletion(document);
            case 'c': return this.handleContextCompletion(runtimeName);
            default: return [];
        }
    }

    private handleQueryCompletion(chain: string[], runtimeName: string | null): vscode.CompletionItem[] {
        if (chain.length === 0) {
            const items: vscode.CompletionItem[] = [];
            const queryVars = this.schema.getQueryVariables(runtimeName);
            for (const [name, obj] of Object.entries(queryVars)) {
                items.push(this.makeFunctionItem(name, obj, obj.type === 'Struct' ? '0' : '1'));
            }
            // Add general functions
            for (const [name, obj] of Object.entries(this.schema.getGeneralFunctions())) {
                items.push(this.makeFunctionItem(name, obj, '2'));
            }
            return items;
        }

        // Resolve chain
        return this.resolveAndGetItems(runtimeName, chain);
    }

    private handleMathCompletion(chain: string[]): vscode.CompletionItem[] {
        if (chain.length === 0) {
            const items: vscode.CompletionItem[] = [];
            for (const [name, obj] of Object.entries(this.schema.getMathFunctions())) {
                items.push(this.makeFunctionItem(name, obj, '0'));
            }
            return items;
        }
        return [];
    }

    private handleTempCompletion(document: vscode.TextDocument): vscode.CompletionItem[] {
        const names = scanPrefixUsages(document.getText(), 't', 'temp');
        return names.map(name => {
            const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Variable);
            item.detail = 'Temp';
            return item;
        });
    }

    private handleVariableCompletion(document: vscode.TextDocument): vscode.CompletionItem[] {
        const names = scanPrefixUsages(document.getText(), 'v', 'variable');
        return names.map(name => {
            const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Field);
            item.detail = 'Variable';
            return item;
        });
    }

    private handleFunctionCompletion(document: vscode.TextDocument): vscode.CompletionItem[] {
        const names = scanFnDefinitions(document.getText());
        return names.map(name => {
            const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Function);
            item.detail = 'fn()';
            item.insertText = new vscode.SnippetString(name + '($0)');
            return item;
        });
    }

    private handleContextCompletion(runtimeName: string | null): vscode.CompletionItem[] {
        if (!runtimeName) return [];
        const queryVars = this.schema.getRuntimeQueryVariables(runtimeName);
        return Object.keys(queryVars).map(name => {
            const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Property);
            item.detail = 'Context';
            return item;
        });
    }

    private resolveAndGetItems(runtimeName: string | null, chain: string[]): vscode.CompletionItem[] {
        const resolution = this.schema.resolveChain(runtimeName, chain);
        if (!resolution) return [];
        const items: vscode.CompletionItem[] = [];
        for (const [name, obj] of Object.entries(resolution.functions)) {
            items.push(this.makeFunctionItem(name, obj, '0'));
        }
        return items;
    }

    private makeFunctionItem(name: string, func: SchemaFunction, sortPrefix: string): vscode.CompletionItem {
        const kind = getCompletionKind(func.type);
        const item = new vscode.CompletionItem(name, kind);

        const returnType = func.returns ?? func.type ?? '';
        item.detail = returnType;

        if (func.struct_type) {
            item.detail += ` (${func.struct_type})`;
        }

        // Build documentation
        const md = new vscode.MarkdownString();
        const paramSig = buildParamSignature(func);
        if (paramSig) {
            md.appendCodeblock(`${name}(${paramSig}) → ${returnType}`, 'molang');
        } else {
            md.appendCodeblock(`${name} → ${returnType}`, 'molang');
        }
        if (func.description) md.appendText('\n' + func.description);
        if (func.source) md.appendText('\n\n*Source: ' + func.source + '*');
        item.documentation = md;

        // Insert with snippet for params
        if (func.params && func.params.length > 0) {
            const snippetParams = func.params.map((p, i) =>
                `\${${i + 1}:${p.name ?? 'arg' + (i + 1)}}`
            ).join(', ');
            item.insertText = new vscode.SnippetString(name + '(' + snippetParams + ')');
        }

        item.sortText = sortPrefix + name;
        return item;
    }

    private handleBareCompletion(): vscode.CompletionItem[] {
        const items: vscode.CompletionItem[] = [];
        const keywords = ['fn', 'if', 'else', 'switch', 'while', 'struct', 'import',
            'return', 'break', 'continue', 'for', 'default', 'true', 'false'];
        for (const kw of keywords) {
            const item = new vscode.CompletionItem(kw, vscode.CompletionItemKind.Keyword);
            item.sortText = '0' + kw;
            items.push(item);
        }
        for (const prefix of ['q', 'v', 't', 'f', 'c', 'math']) {
            const item = new vscode.CompletionItem(prefix, vscode.CompletionItemKind.Module);
            item.detail = 'prefix';
            item.insertText = prefix + '.';
            item.command = { command: 'editor.action.triggerSuggest', title: '' };
            item.sortText = '1' + prefix;
            items.push(item);
        }
        return items;
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

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

function getCompletionKind(type?: string): vscode.CompletionItemKind {
    switch (type) {
        case 'Number': return vscode.CompletionItemKind.Field;
        case 'String': return vscode.CompletionItemKind.Variable;
        case 'Struct': return vscode.CompletionItemKind.Class;
        case 'Unit': case 'Void': return vscode.CompletionItemKind.Method;
        default: return vscode.CompletionItemKind.Function;
    }
}

function scanPrefixUsages(text: string, shortPrefix: string, longPrefix: string): string[] {
    const names = new Set<string>();
    const pattern = new RegExp(`(?:${shortPrefix}|${longPrefix})\\.([a-zA-Z_][a-zA-Z0-9_]*)`, 'g');
    let m: RegExpExecArray | null;
    while ((m = pattern.exec(text)) !== null) {
        names.add(m[1]);
    }
    return [...names];
}

function scanFnDefinitions(text: string): string[] {
    const names = new Set<string>();
    const pattern = /fn\s*\(\s*'([^']+)'/g;
    let m: RegExpExecArray | null;
    while ((m = pattern.exec(text)) !== null) {
        names.add(m[1]);
    }
    return [...names];
}

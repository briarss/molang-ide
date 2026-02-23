import * as vscode from 'vscode';

const FN_CALL_PATTERN = /(?:f|function)\.([a-zA-Z_][a-zA-Z0-9_]*)/;
const IMPORT_PATTERN = /import\s*\(\s*'([^']+)'\s*\)/;
const FN_DEF_PATTERN = /fn\s*\(\s*'([^']+)'/;

export class MoLangDefinitionProvider implements vscode.DefinitionProvider {
    async provideDefinition(
        document: vscode.TextDocument,
        position: vscode.Position,
        _token: vscode.CancellationToken
    ): Promise<vscode.Definition | undefined> {
        const lineText = document.lineAt(position).text;

        // Check for f.xxx() call — find the fn('xxx', ...) definition
        const fnResult = await this.tryFunctionDefinition(document, position, lineText);
        if (fnResult) return fnResult;

        // Check for import('namespace:path') — resolve to file
        const importResult = await this.tryImportDefinition(position, lineText);
        if (importResult) return importResult;

        return undefined;
    }

    private async tryFunctionDefinition(
        document: vscode.TextDocument,
        position: vscode.Position,
        lineText: string
    ): Promise<vscode.Location[] | undefined> {
        // Find f.xxx pattern around cursor
        const wordRange = document.getWordRangeAtPosition(position, /(?:f|function)\.([a-zA-Z_][a-zA-Z0-9_]*)/);
        if (!wordRange) return undefined;

        const matchText = document.getText(wordRange);
        const m = FN_CALL_PATTERN.exec(matchText);
        if (!m) return undefined;

        const fnName = m[1];

        // Search for fn('fnName', ...) definitions in workspace
        const locations: vscode.Location[] = [];

        // Search current document first
        const docLocations = this.findFnDefinitionsInDocument(document, fnName);
        locations.push(...docLocations);

        // Search workspace .molang files
        const files = await vscode.workspace.findFiles('**/*.molang', '**/node_modules/**', 50);
        for (const uri of files) {
            if (uri.toString() === document.uri.toString()) continue;
            try {
                const doc = await vscode.workspace.openTextDocument(uri);
                const locs = this.findFnDefinitionsInDocument(doc, fnName);
                locations.push(...locs);
            } catch {
                // skip unreadable files
            }
        }

        return locations.length > 0 ? locations : undefined;
    }

    private findFnDefinitionsInDocument(document: vscode.TextDocument, fnName: string): vscode.Location[] {
        const locations: vscode.Location[] = [];
        const text = document.getText();
        const pattern = new RegExp(`fn\\s*\\(\\s*'${escapeRegExp(fnName)}'`, 'g');
        let m: RegExpExecArray | null;
        while ((m = pattern.exec(text)) !== null) {
            const pos = document.positionAt(m.index);
            locations.push(new vscode.Location(document.uri, pos));
        }
        return locations;
    }

    private async tryImportDefinition(
        position: vscode.Position,
        lineText: string
    ): Promise<vscode.Location | undefined> {
        // Check if cursor is inside an import('...')
        const m = IMPORT_PATTERN.exec(lineText);
        if (!m) return undefined;

        const importStart = m.index;
        const importEnd = importStart + m[0].length;
        if (position.character < importStart || position.character > importEnd) return undefined;

        const importPath = m[1]; // e.g. "namespace:path"
        const colonIdx = importPath.indexOf(':');
        if (colonIdx < 0) return undefined;

        const namespace = importPath.substring(0, colonIdx);
        const path = importPath.substring(colonIdx + 1);

        // Resolve to data/{namespace}/molang/{path}.molang
        const glob = `**/data/${namespace}/molang/${path}.molang`;
        const files = await vscode.workspace.findFiles(glob, '**/node_modules/**', 1);
        if (files.length > 0) {
            return new vscode.Location(files[0], new vscode.Position(0, 0));
        }

        return undefined;
    }
}

function escapeRegExp(str: string): string {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

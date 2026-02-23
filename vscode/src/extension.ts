import * as vscode from 'vscode';
import { MoLangSchemaService } from './schema';
import { MoLangCompletionProvider } from './completionProvider';
import { MoLangHoverProvider } from './hoverProvider';
import { MoLangDefinitionProvider } from './definitionProvider';

const MOLANG_SELECTOR: vscode.DocumentSelector = { language: 'molang', scheme: 'file' };

export function activate(context: vscode.ExtensionContext) {
    context.subscriptions.push(
        vscode.languages.registerDefinitionProvider(
            MOLANG_SELECTOR,
            new MoLangDefinitionProvider()
        )
    );

    const schema = new MoLangSchemaService();
    schema.load(context.extensionPath);

    if (!schema.isLoaded()) {
        console.warn('MoLang schema failed to load — completions/hover will be unavailable');
        return;
    }

    context.subscriptions.push(
        vscode.languages.registerCompletionItemProvider(
            MOLANG_SELECTOR,
            new MoLangCompletionProvider(schema),
            '.'
        ),
        vscode.languages.registerHoverProvider(
            MOLANG_SELECTOR,
            new MoLangHoverProvider(schema)
        )
    );

    console.log('MoLang extension activated with schema (' +
        schema.getRuntimeNames().length + ' runtimes)');
}

export function deactivate() {}

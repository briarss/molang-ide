# MoLang IDE

IDE plugins for the MoLang scripting language (Cobblemon/Bedrock). Provides schema-driven completions, hover documentation, go-to-definition, and context-aware query variable scoping.

## Plugins

### IntelliJ IDEA (`intellij/`)

Full language plugin for IntelliJ-based IDEs.

**Features:**
- Syntax highlighting for `.molang` files
- Schema-driven code completion for `q.`, `math.`, `v.`, `t.`, `f.`, `c.` prefixes
- Deep chain resolution (e.g. `q.pokemon.species.name` resolves through struct types)
- Hover documentation with signatures, parameters, return types, and source info
- Go-to-definition for `fn()` and `import()` references
- Context-aware completions via `// @context` annotations or folder-based inference

**Build & Install:**
```bash
cd intellij
./gradlew buildPlugin
```
Install the zip from `build/distributions/` via Settings > Plugins > gear icon > "Install Plugin from Disk..."

### VS Code (`vscode/`)

TypeScript extension for Visual Studio Code.

**Features:**
- Syntax highlighting (TextMate grammar)
- Schema-driven code completion with snippet insertion for function parameters
- Hover documentation with Markdown formatting
- Go-to-definition for `f.xxx()` calls and `import('namespace:path')` references
- Context-aware completions via `// @context` annotations or folder-based inference
- 14 built-in snippets for common patterns

**Build & Install:**
```bash
cd vscode
npm install
npm run compile
npm run package
```
Install the `.vsix` via `code --install-extension molang-2.0.0.vsix` or Extensions > "Install from VSIX..."

**Development:** Press F5 in VS Code to launch an Extension Development Host with the plugin loaded.

## Context-Aware Completions

Add a `// @context` annotation to the top of any `.molang` file to scope completions to a specific runtime:

```molang
// @context event:BATTLE_VICTORY
```

With this, `q.` completions show only that event's query variables (e.g. `player`, `battle`). Without it, all runtimes are merged for broad suggestions. Context is also auto-detected from folder paths like `callbacks/{event}/`.

## Schema

Both plugins are powered by `molang-schema.json`, a comprehensive schema defining:
- **Structs** - pokemon, player, species, battle, etc. with all their functions
- **Function sets** - composed registries (entityFunctions, playerFunctions, etc.)
- **Runtimes** - 129 event contexts with specific query variables
- **Struct compositions** - how struct types are assembled from function registries
- **Math functions** - full trig/interpolation/utility library

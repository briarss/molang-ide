import * as path from 'path';
import * as fs from 'fs';

// ── Types ────────────────────────────────────────────────────────────

export interface SchemaFunction {
    type?: string;
    returns?: string;
    params?: SchemaParam[];
    description?: string;
    source?: string;
    struct_type?: string;
    functions?: Record<string, SchemaFunction>;
}

export interface SchemaParam {
    name?: string;
    type?: string;
    description?: string;
    optional?: boolean;
}

interface SchemaStruct {
    description?: string;
    functions?: Record<string, SchemaFunction>;
}

interface SchemaRuntime {
    description?: string;
    category?: string;
    query?: Record<string, SchemaFunction>;
}

interface SchemaComposition {
    description?: string;
    registries?: string[];
    custom_functions?: Record<string, SchemaFunction>;
}

interface SchemaFunctionSet {
    description?: string;
    functions?: Record<string, SchemaFunction>;
}

interface SchemaRoot {
    structs?: Record<string, SchemaStruct>;
    function_sets?: Record<string, SchemaFunctionSet>;
    runtimes?: Record<string, SchemaRuntime>;
    structCompositions?: Record<string, SchemaComposition>;
}

export interface SchemaResolution {
    entry: SchemaFunction | null;
    functions: Record<string, SchemaFunction>;
}

// ── Schema Service ───────────────────────────────────────────────────

export class MoLangSchemaService {
    private root: SchemaRoot = {};
    private structs: Record<string, SchemaStruct> = {};
    private functionSets: Record<string, SchemaFunctionSet> = {};
    private runtimes: Record<string, SchemaRuntime> = {};
    private structCompositions: Record<string, SchemaComposition> = {};
    private loaded = false;

    load(extensionPath: string): void {
        if (this.loaded) return;
        try {
            const schemaPath = path.join(extensionPath, 'schema', 'molang-schema.json');
            const raw = fs.readFileSync(schemaPath, 'utf-8');
            this.root = JSON.parse(raw);
            this.structs = this.root.structs ?? {};
            this.functionSets = this.root.function_sets ?? {};
            this.runtimes = this.root.runtimes ?? {};
            this.structCompositions = this.root.structCompositions ?? {};
            this.loaded = true;
        } catch (e) {
            console.error('Failed to load molang-schema.json', e);
        }
    }

    isLoaded(): boolean {
        return this.loaded;
    }

    // ── Runtime contexts ─────────────────────────────────────────────

    getRuntimeNames(): string[] {
        return Object.keys(this.runtimes);
    }

    getRuntimeContext(eventName: string): SchemaRuntime | undefined {
        return this.runtimes[eventName];
    }

    getRuntimeQueryVariables(eventName: string): Record<string, SchemaFunction> {
        const runtime = this.runtimes[eventName];
        if (!runtime?.query) return {};
        return runtime.query;
    }

    /**
     * Infer runtime context from `// @context event:XXX` annotation in first 10 lines.
     */
    inferRuntimeFromContent(text: string): string | null {
        const lines = text.split('\n', 12);
        const limit = Math.min(lines.length, 10);
        const pattern = /\/\/\s*@context\s+(\S+)/;
        for (let i = 0; i < limit; i++) {
            const m = pattern.exec(lines[i].trim());
            if (m) {
                const ctx = m[1];
                if (ctx in this.runtimes) {
                    return ctx;
                }
            }
        }
        return null;
    }

    /**
     * Infer runtime context from file path patterns.
     */
    inferRuntimeFromPath(filePath: string): string | null {
        const normalized = filePath.replace(/\\/g, '/').toLowerCase();

        // Try callbacks/event_name/ or molang/event_name/ pattern
        for (const prefix of ['callbacks/', 'molang/']) {
            const idx = normalized.indexOf(prefix);
            if (idx >= 0) {
                const after = normalized.substring(idx + prefix.length);
                const slashIdx = after.indexOf('/');
                if (slashIdx > 0) {
                    const folder = after.substring(0, slashIdx);
                    const eventName = 'event:' + folder.toUpperCase();
                    if (eventName in this.runtimes) {
                        return eventName;
                    }
                }
            }
        }

        // Fuzzy match against runtime names
        for (const runtimeName of Object.keys(this.runtimes)) {
            const lower = runtimeName.replace('event:', '').toLowerCase().replace(/_/g, '');
            if (normalized.includes(lower)) {
                return runtimeName;
            }
        }

        return null;
    }

    // ── Structs ──────────────────────────────────────────────────────

    getStructNames(): string[] {
        return Object.keys(this.structs);
    }

    getStructFunctions(structName: string): Record<string, SchemaFunction> {
        const s = this.structs[structName];
        if (!s?.functions) return {};
        return s.functions;
    }

    // ── Struct compositions ──────────────────────────────────────────

    getCompositionRegistries(structType: string): string[] {
        const comp = this.structCompositions[structType];
        if (!comp?.registries) return [];
        return comp.registries;
    }

    // ── Function sets ────────────────────────────────────────────────

    getFunctionSetFunctions(setName: string): Record<string, SchemaFunction> {
        const set = this.functionSets[setName];
        if (!set?.functions) return {};
        return set.functions;
    }

    // ── Chain resolution ─────────────────────────────────────────────

    resolveChain(runtimeName: string | null, chain: string[]): SchemaResolution | null {
        if (!chain || chain.length === 0) return null;

        const queryVars = this.getQueryVariables(runtimeName);

        let current: SchemaFunction | null = null;
        let currentStructType: string | null = null;
        let currentFunctions: Record<string, SchemaFunction> | null = null;

        // Resolve first element
        const first = chain[0];
        if (first in queryVars) {
            current = queryVars[first];
            currentStructType = current.struct_type ?? first;
        } else if (first in this.structs) {
            currentStructType = first;
        } else {
            return null;
        }

        // Walk remaining chain elements
        for (let i = 1; i < chain.length; i++) {
            let funcs: Record<string, SchemaFunction>;
            if (currentFunctions) {
                funcs = currentFunctions;
                currentFunctions = null;
            } else {
                funcs = this.getAllFunctionsForType(currentStructType);
            }

            const member = chain[i];
            if (!(member in funcs)) return null;

            current = funcs[member];
            const type = current.type;
            if (type === 'Struct') {
                currentStructType = current.struct_type ?? null;
                if (current.functions) {
                    currentFunctions = this.getInlineFunctions(current);
                }
                if (!currentStructType && !currentFunctions) return null;
            } else {
                if (i < chain.length - 1) return null;
                return { entry: current, functions: {} };
            }
        }

        // Return functions available at this point
        let availableFunctions: Record<string, SchemaFunction>;
        if (currentFunctions) {
            availableFunctions = currentFunctions;
        } else if (current?.functions) {
            availableFunctions = this.getInlineFunctions(current);
        } else {
            availableFunctions = this.getAllFunctionsForType(currentStructType);
        }
        return { entry: current, functions: availableFunctions };
    }

    /**
     * Resolve a single function entry by full chain (for documentation lookup).
     */
    resolveFunction(runtimeName: string | null, chain: string[]): SchemaFunction | null {
        if (!chain || chain.length === 0) return null;

        if (chain.length === 1 && chain[0] === 'math') return null;

        const parentChain = chain.slice(0, chain.length - 1);
        let res = this.resolveChain(runtimeName, parentChain);
        if (!res && chain.length === 1) {
            if (runtimeName) {
                const queryVars = this.getRuntimeQueryVariables(runtimeName);
                return queryVars[chain[0]] ?? null;
            }
            return null;
        }
        if (!res) return null;

        const last = chain[chain.length - 1];
        return res.functions[last] ?? null;
    }

    // ── Math functions ───────────────────────────────────────────────

    getMathFunctions(): Record<string, SchemaFunction> {
        return this.getStructFunctions('math');
    }

    // ── General functions ────────────────────────────────────────────

    getGeneralFunctions(): Record<string, SchemaFunction> {
        return this.getFunctionSetFunctions('generalFunctions');
    }

    // ── Helpers ──────────────────────────────────────────────────────

    getAllFunctionsForType(structType: string | null): Record<string, SchemaFunction> {
        if (!structType) return {};

        const result: Record<string, SchemaFunction> = {};

        // Direct struct functions
        Object.assign(result, this.getStructFunctions(structType));

        // Composed function set functions
        const registries = this.getCompositionRegistries(structType);
        for (const registry of registries) {
            Object.assign(result, this.getFunctionSetFunctions(registry));
        }

        // Custom functions from composition
        const comp = this.structCompositions[structType];
        if (comp?.custom_functions) {
            Object.assign(result, comp.custom_functions);
        }

        return result;
    }

    getQueryVariables(runtimeName: string | null): Record<string, SchemaFunction> {
        if (runtimeName) {
            return this.getRuntimeQueryVariables(runtimeName);
        }
        // Merge from all runtimes for broad completions
        const merged: Record<string, SchemaFunction> = {};
        for (const key of Object.keys(this.runtimes)) {
            Object.assign(merged, this.getRuntimeQueryVariables(key));
        }
        return merged;
    }

    private getInlineFunctions(parent: SchemaFunction): Record<string, SchemaFunction> {
        const result: Record<string, SchemaFunction> = {};

        if (parent.functions) {
            Object.assign(result, parent.functions);
        }

        // Also include composed functions if struct_type is set
        if (parent.struct_type) {
            const composed = this.getAllFunctionsForType(parent.struct_type);
            for (const [key, value] of Object.entries(composed)) {
                if (!(key in result)) {
                    result[key] = value;
                }
            }
        }

        return result;
    }
}

package aster.amo.molang.ide.schema;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service(Service.Level.PROJECT)
public final class MoLangSchemaService {
    private static final Logger LOG = Logger.getInstance(MoLangSchemaService.class);

    private JsonObject root;
    private JsonObject structs;
    private JsonObject functionSets;
    private JsonObject runtimes;
    private JsonObject structCompositions;
    private boolean loaded = false;

    public MoLangSchemaService(@NotNull Project project) {
        loadSchema();
    }

    private synchronized void loadSchema() {
        if (loaded) return;
        try (InputStream is = getClass().getResourceAsStream("/schema/molang-schema.json")) {
            if (is == null) {
                LOG.warn("molang-schema.json not found in resources");
                return;
            }
            root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            structs = root.has("structs") ? root.getAsJsonObject("structs") : new JsonObject();
            functionSets = root.has("function_sets") ? root.getAsJsonObject("function_sets") : new JsonObject();
            runtimes = root.has("runtimes") ? root.getAsJsonObject("runtimes") : new JsonObject();
            structCompositions = root.has("structCompositions") ? root.getAsJsonObject("structCompositions") : new JsonObject();
            loaded = true;
            LOG.info("MoLang schema loaded: " + runtimes.size() + " runtimes, " + structs.size() + " structs");
        } catch (Exception e) {
            LOG.error("Failed to load molang-schema.json", e);
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    // ── Runtime contexts ──────────────────────────────────────────────

    /**
     * Get all runtime context names (e.g., "event:BATTLE_VICTORY").
     */
    public Set<String> getRuntimeNames() {
        return runtimes != null ? runtimes.keySet() : Collections.emptySet();
    }

    /**
     * Get the runtime context object for a given event name.
     */
    @Nullable
    public JsonObject getRuntimeContext(String eventName) {
        if (runtimes == null || !runtimes.has(eventName)) return null;
        return runtimes.getAsJsonObject(eventName);
    }

    /**
     * Get the query variables available in a runtime context.
     * Returns a map of variable name → JsonObject with type/struct_type/description.
     */
    @NotNull
    public Map<String, JsonObject> getRuntimeQueryVariables(String eventName) {
        JsonObject runtime = getRuntimeContext(eventName);
        if (runtime == null || !runtime.has("query")) return Collections.emptyMap();
        JsonObject query = runtime.getAsJsonObject("query");
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (String key : query.keySet()) {
            JsonElement el = query.get(key);
            if (el.isJsonObject()) {
                result.put(key, el.getAsJsonObject());
            }
        }
        return result;
    }

    /**
     * Try to infer the runtime context from a {@code // @context event:XXX} annotation
     * in the first 10 lines of the document text.
     */
    @Nullable
    public String inferRuntimeFromContent(String text) {
        if (text == null) return null;
        String[] lines = text.split("\n", 12);
        int limit = Math.min(lines.length, 10);
        Pattern p = Pattern.compile("//\\s*@context\\s+(\\S+)");
        for (int i = 0; i < limit; i++) {
            Matcher m = p.matcher(lines[i].trim());
            if (m.find()) {
                String ctx = m.group(1);
                if (runtimes != null && runtimes.has(ctx)) {
                    return ctx;
                }
            }
        }
        return null;
    }

    /**
     * Try to infer the runtime context from a file path.
     * Patterns:
     *   callbacks/{event_name_lower}/*.molang → event:EVENT_NAME_UPPER
     *   molang/{event_name_lower}/*.molang    → event:EVENT_NAME_UPPER
     *   Fuzzy match against any runtime name in the path
     */
    @Nullable
    public String inferRuntimeFromPath(String filePath) {
        if (filePath == null) return null;
        String normalized = filePath.replace('\\', '/').toLowerCase();

        // Try callbacks/event_name/ or molang/event_name/ pattern
        for (String prefix : new String[]{"callbacks/", "molang/"}) {
            int idx = normalized.indexOf(prefix);
            if (idx >= 0) {
                String after = normalized.substring(idx + prefix.length());
                int slashIdx = after.indexOf('/');
                if (slashIdx > 0) {
                    String folder = after.substring(0, slashIdx);
                    String eventName = "event:" + folder.toUpperCase();
                    if (runtimes != null && runtimes.has(eventName)) {
                        return eventName;
                    }
                }
            }
        }

        // Try matching any runtime category from the path
        if (runtimes != null) {
            for (String runtimeName : runtimes.keySet()) {
                String lower = runtimeName.replace("event:", "").toLowerCase().replace("_", "");
                if (normalized.contains(lower)) {
                    return runtimeName;
                }
            }
        }

        return null;
    }

    // ── Structs ───────────────────────────────────────────────────────

    /**
     * Get top-level struct names (pokemon, math, player, npc, species).
     */
    public Set<String> getStructNames() {
        return structs != null ? structs.keySet() : Collections.emptySet();
    }

    /**
     * Get the functions available on a struct.
     * Returns a map of function name → JsonObject with type, params, description, source.
     */
    @NotNull
    public Map<String, JsonObject> getStructFunctions(String structName) {
        if (structs == null || !structs.has(structName)) return Collections.emptyMap();
        JsonObject struct = structs.getAsJsonObject(structName);
        if (!struct.has("functions")) return Collections.emptyMap();
        JsonObject functions = struct.getAsJsonObject("functions");
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (String key : functions.keySet()) {
            JsonElement el = functions.get(key);
            if (el.isJsonObject()) {
                result.put(key, el.getAsJsonObject());
            }
        }
        return result;
    }

    // ── Struct compositions ───────────────────────────────────────────

    /**
     * Get the function set registries that compose a struct type.
     */
    @NotNull
    public List<String> getCompositionRegistries(String structType) {
        if (structCompositions == null || !structCompositions.has(structType)) return Collections.emptyList();
        JsonObject comp = structCompositions.getAsJsonObject(structType);
        if (!comp.has("registries")) return Collections.emptyList();
        List<String> registries = new ArrayList<>();
        for (JsonElement el : comp.getAsJsonArray("registries")) {
            registries.add(el.getAsString());
        }
        return registries;
    }

    // ── Function sets ─────────────────────────────────────────────────

    /**
     * Get all functions from a named function set.
     */
    @NotNull
    public Map<String, JsonObject> getFunctionSetFunctions(String setName) {
        if (functionSets == null || !functionSets.has(setName)) return Collections.emptyMap();
        JsonObject set = functionSets.getAsJsonObject(setName);
        if (!set.has("functions")) return Collections.emptyMap();
        JsonObject functions = set.getAsJsonObject("functions");
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (String key : functions.keySet()) {
            JsonElement el = functions.get(key);
            if (el.isJsonObject()) {
                result.put(key, el.getAsJsonObject());
            }
        }
        return result;
    }

    // ── Chain resolution ──────────────────────────────────────────────

    /**
     * Resolve a dot-chain like ["pokemon", "species", "identifier"]
     * starting from a given struct type or runtime query variable.
     *
     * Returns a SchemaResolution with the functions available at the end of the chain,
     * or null if resolution fails.
     */
    @Nullable
    public SchemaResolution resolveChain(String runtimeName, String[] chain) {
        if (chain == null || chain.length == 0) return null;

        // First element: look it up in runtime query vars
        Map<String, JsonObject> queryVars = getQueryVariables(runtimeName);

        // Walk the chain
        JsonObject current = null;
        String currentStructType = null;
        Map<String, JsonObject> currentFunctions = null; // for inline function nodes

        // Resolve first element
        String first = chain[0];
        if (queryVars.containsKey(first)) {
            current = queryVars.get(first);
            currentStructType = getStringField(current, "struct_type");
            if (currentStructType == null) {
                currentStructType = first;
            }
        } else if (structs != null && structs.has(first)) {
            currentStructType = first;
        } else {
            return null;
        }

        // Walk remaining chain elements
        for (int i = 1; i < chain.length; i++) {
            Map<String, JsonObject> funcs;
            if (currentFunctions != null) {
                funcs = currentFunctions;
                currentFunctions = null;
            } else {
                funcs = getAllFunctionsForType(currentStructType);
            }

            String member = chain[i];
            if (!funcs.containsKey(member)) return null;

            current = funcs.get(member);
            String type = getStringField(current, "type");
            if ("Struct".equals(type)) {
                currentStructType = getStringField(current, "struct_type");
                if (current.has("functions")) {
                    // Merge inline functions with composed functions from struct_type
                    currentFunctions = getInlineFunctions(current);
                }
                if (currentStructType == null && currentFunctions == null) return null;
            } else {
                // Terminal value, not a struct
                if (i < chain.length - 1) return null;
                return new SchemaResolution(current, Collections.emptyMap());
            }
        }

        // Return functions available at this point
        Map<String, JsonObject> availableFunctions;
        if (currentFunctions != null) {
            availableFunctions = currentFunctions;
        } else if (current != null && current.has("functions")) {
            availableFunctions = getInlineFunctions(current);
        } else {
            availableFunctions = getAllFunctionsForType(currentStructType);
        }
        return new SchemaResolution(current, availableFunctions);
    }

    /**
     * Resolve a single function entry by full chain (for documentation lookup).
     */
    @Nullable
    public JsonObject resolveFunction(String runtimeName, String[] chain) {
        if (chain == null || chain.length == 0) return null;

        // For math.xxx
        if (chain.length >= 1 && "math".equals(chain[0]) && chain.length == 1) {
            return null; // math itself
        }

        SchemaResolution res = resolveChain(runtimeName, Arrays.copyOf(chain, chain.length - 1));
        if (res == null && chain.length == 1) {
            // Direct lookup in runtime query vars
            if (runtimeName != null) {
                Map<String, JsonObject> queryVars = getRuntimeQueryVariables(runtimeName);
                return queryVars.get(chain[0]);
            }
            return null;
        }
        if (res == null) return null;

        String last = chain[chain.length - 1];
        return res.functions().get(last);
    }

    // ── Math functions ────────────────────────────────────────────────

    @NotNull
    public Map<String, JsonObject> getMathFunctions() {
        return getStructFunctions("math");
    }

    // ── General functions (available everywhere) ──────────────────────

    @NotNull
    public Map<String, JsonObject> getGeneralFunctions() {
        return getFunctionSetFunctions("generalFunctions");
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Get all functions available on a struct type, including composed function sets.
     */
    @NotNull
    public Map<String, JsonObject> getAllFunctionsForType(@Nullable String structType) {
        if (structType == null) return Collections.emptyMap();

        Map<String, JsonObject> result = new LinkedHashMap<>();

        // Direct struct functions
        result.putAll(getStructFunctions(structType));

        // Composed function set functions
        List<String> registries = getCompositionRegistries(structType);
        for (String registry : registries) {
            result.putAll(getFunctionSetFunctions(registry));
        }

        // Custom functions from composition (e.g. vec3.x/y/z, zone.name/uuid)
        if (structCompositions != null && structCompositions.has(structType)) {
            JsonObject comp = structCompositions.getAsJsonObject(structType);
            if (comp.has("custom_functions")) {
                JsonObject customFuncs = comp.getAsJsonObject("custom_functions");
                for (String key : customFuncs.keySet()) {
                    JsonElement el = customFuncs.get(key);
                    if (el.isJsonObject()) {
                        result.putIfAbsent(key, el.getAsJsonObject());
                    }
                }
            }
        }

        return result;
    }

    /**
     * Get all available query variables for completions.
     * If runtime is known, uses runtime-specific variables.
     * Otherwise, returns a merged set from all common runtimes.
     */
    @NotNull
    public Map<String, JsonObject> getQueryVariables(@Nullable String runtimeName) {
        if (runtimeName != null) {
            return getRuntimeQueryVariables(runtimeName);
        }
        // Merge from common runtimes to provide broad completions
        Map<String, JsonObject> merged = new LinkedHashMap<>();
        if (runtimes != null) {
            for (String key : runtimes.keySet()) {
                merged.putAll(getRuntimeQueryVariables(key));
            }
        }
        return merged;
    }

    @NotNull
    private Map<String, JsonObject> getInlineFunctions(JsonObject parent) {
        if (!parent.has("functions")) return Collections.emptyMap();
        JsonObject functions = parent.getAsJsonObject("functions");
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (String key : functions.keySet()) {
            JsonElement el = functions.get(key);
            if (el.isJsonObject()) {
                result.put(key, el.getAsJsonObject());
            }
        }

        // Also include composed functions if struct_type is set
        String structType = getStringField(parent, "struct_type");
        if (structType != null) {
            Map<String, JsonObject> composed = getAllFunctionsForType(structType);
            for (var entry : composed.entrySet()) {
                result.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    @Nullable
    private static String getStringField(JsonObject obj, String field) {
        if (obj == null || !obj.has(field)) return null;
        JsonElement el = obj.get(field);
        return el.isJsonPrimitive() ? el.getAsString() : null;
    }

    // ── Resolution result ─────────────────────────────────────────────

    public record SchemaResolution(
            @Nullable JsonObject entry,
            @NotNull Map<String, JsonObject> functions
    ) {}
}

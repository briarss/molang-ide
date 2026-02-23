package aster.amo.molang.ide.documentation;

import aster.amo.molang.ide.MoLangLanguage;
import aster.amo.molang.ide.schema.MoLangSchemaService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MoLangDocumentationProvider extends AbstractDocumentationProvider {

    private static final Set<String> KEYWORD_SET = Set.of(
            "fn", "if", "else", "switch", "while", "struct", "import",
            "return", "break", "continue", "for", "default"
    );

    private static final Pattern CHAIN_PATTERN = Pattern.compile(
            "(q|query|v|variable|t|temp|f|function|c|context|math)" +
            "((?:\\.[a-zA-Z_][a-zA-Z0-9_]*)+)"
    );

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        if (originalElement == null) return null;
        PsiFile file = originalElement.getContainingFile();
        if (file == null || file.getLanguage() != MoLangLanguage.INSTANCE) return null;

        Project project = element.getProject();
        MoLangSchemaService schema = project.getService(MoLangSchemaService.class);
        if (schema == null || !schema.isLoaded()) return null;

        String elementText = originalElement.getText();
        Document doc = file.getViewProvider().getDocument();
        if (doc == null) return null;

        int offset = originalElement.getTextOffset();
        String fullText = doc.getText();

        String chain = extractChainAt(fullText, offset);
        if (chain == null) {
            if (KEYWORD_SET.contains(elementText)) {
                return generateKeywordDoc(elementText);
            }
            return null;
        }

        Matcher m = CHAIN_PATTERN.matcher(chain);
        if (!m.matches()) return null;

        String prefix = normalizePrefix(m.group(1));
        String dotPart = m.group(2);
        String[] parts = dotPart.substring(1).split("\\.");

        String runtimeName = schema.inferRuntimeFromContent(fullText);
        if (runtimeName == null) {
            VirtualFile vFile = file.getVirtualFile();
            runtimeName = vFile != null ? schema.inferRuntimeFromPath(vFile.getPath()) : null;
        }

        if ("math".equals(prefix)) {
            if (parts.length >= 1) {
                Map<String, JsonObject> mathFuncs = schema.getMathFunctions();
                JsonObject func = mathFuncs.get(parts[0]);
                if (func != null) {
                    return generateFunctionDoc("math." + parts[0], func);
                }
            }
            return null;
        }

        if ("q".equals(prefix)) {
            JsonObject resolved = schema.resolveFunction(runtimeName, parts);
            if (resolved != null) {
                return generateFunctionDoc("q." + String.join(".", parts), resolved);
            }

            if (parts.length == 1) {
                Map<String, JsonObject> queryVars = schema.getQueryVariables(runtimeName);
                JsonObject qv = queryVars.get(parts[0]);
                if (qv != null) {
                    return generateFunctionDoc("q." + parts[0], qv);
                }
            }
        }

        return null;
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        String doc = generateDoc(element, originalElement);
        if (doc == null) return null;
        return doc.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    private String generateFunctionDoc(String fullName, JsonObject func) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");

        String type = getStringField(func, "type");
        String returns = getStringField(func, "returns");
        String returnType = returns != null ? returns : (type != null ? type : "Unknown");

        sb.append("<b><code>");
        sb.append(escapeHtml(fullName));

        String paramSig = buildParamSignature(func);
        if (!paramSig.isEmpty()) {
            sb.append("(").append(escapeHtml(paramSig)).append(")");
        }

        sb.append(" → ").append(escapeHtml(returnType));
        sb.append("</code></b>");

        String desc = getStringField(func, "description");
        if (desc != null) {
            sb.append("<br/><br/>").append(escapeHtml(desc));
        }

        String source = getStringField(func, "source");
        if (source != null) {
            sb.append("<br/><br/><i>Source: ").append(escapeHtml(source)).append("</i>");
        }

        String structType = getStringField(func, "struct_type");
        if (structType != null) {
            sb.append("<br/><i>Struct type: ").append(escapeHtml(structType)).append("</i>");
        }

        if (func.has("params") && func.get("params").isJsonArray()) {
            JsonArray params = func.getAsJsonArray("params");
            if (!params.isEmpty()) {
                sb.append("<br/><br/><b>Parameters:</b><br/>");
                sb.append("<table>");
                for (JsonElement el : params) {
                    JsonObject param = el.getAsJsonObject();
                    String pName = getStringField(param, "name");
                    String pType = getStringField(param, "type");
                    String pDesc = getStringField(param, "description");
                    boolean optional = param.has("optional") && param.get("optional").getAsBoolean();
                    sb.append("<tr><td><code>").append(escapeHtml(pName != null ? pName : "?")).append("</code></td>");
                    sb.append("<td>").append(escapeHtml(pType != null ? pType : "")).append(optional ? " (optional)" : "").append("</td>");
                    if (pDesc != null) {
                        sb.append("<td>").append(escapeHtml(pDesc)).append("</td>");
                    }
                    sb.append("</tr>");
                }
                sb.append("</table>");
            }
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private String generateKeywordDoc(String keyword) {
        String desc = switch (keyword) {
            case "fn" -> "<b><code>fn('name', (params) -> { body })</code></b><br/><br/>Defines a named function that can be called with <code>f.name()</code>.";
            case "if" -> "<b><code>if (condition) { then } else { otherwise }</code></b><br/><br/>Conditional execution. Returns the value of the executed branch.";
            case "else" -> "Part of an <code>if/else</code> statement.";
            case "switch" -> "<b><code>switch (value) { case1 : result1; case2 : result2; default : fallback }</code></b><br/><br/>Pattern matching on a value.";
            case "while" -> "<b><code>while (condition) { body }</code></b><br/><br/>Loop that executes body while condition is truthy.";
            case "struct" -> "<b><code>struct { key1 : value1, key2 : value2 }</code></b><br/><br/>Creates a structured data object.";
            case "import" -> "<b><code>import('namespace:path')</code></b><br/><br/>Imports a MoLang script from <code>data/{namespace}/molang/{path}.molang</code>.";
            case "return" -> "Returns a value from the current function or script.";
            case "break" -> "Breaks out of the current loop.";
            case "continue" -> "Skips to the next iteration of the current loop.";
            case "for" -> "<b><code>for (init; condition; step) { body }</code></b><br/><br/>Loop with initialization, condition, and step.";
            case "default" -> "Default case in a switch statement.";
            default -> keyword;
        };
        return "<html><body>" + desc + "</body></html>";
    }

    @Nullable
    private String extractChainAt(String text, int offset) {
        int start = offset;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                start--;
            } else {
                break;
            }
        }

        int end = offset;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (Character.isLetterOrDigit(c) || c == '_') {
                end++;
            } else {
                break;
            }
        }

        if (start >= end) return null;
        String chain = text.substring(start, end);

        if (!chain.contains(".")) return null;
        return chain;
    }

    private static String normalizePrefix(String raw) {
        return switch (raw) {
            case "query" -> "q";
            case "variable" -> "v";
            case "temp" -> "t";
            case "function" -> "f";
            case "context" -> "c";
            default -> raw;
        };
    }

    private static String buildParamSignature(JsonObject func) {
        if (!func.has("params")) return "";
        JsonElement paramsEl = func.get("params");
        if (!paramsEl.isJsonArray()) return "";
        JsonArray params = paramsEl.getAsJsonArray();
        if (params.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            JsonObject param = params.get(i).getAsJsonObject();
            String pName = getStringField(param, "name");
            String pType = getStringField(param, "type");
            boolean optional = param.has("optional") && param.get("optional").getAsBoolean();
            if (pName != null) sb.append(pName);
            if (pType != null) sb.append(": ").append(pType);
            if (optional) sb.append("?");
        }
        return sb.toString();
    }

    @Nullable
    private static String getStringField(JsonObject obj, String field) {
        if (obj == null || !obj.has(field)) return null;
        JsonElement el = obj.get(field);
        return el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

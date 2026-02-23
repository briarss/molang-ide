package aster.amo.molang.ide.completion;

import aster.amo.molang.ide.MoLangLanguage;
import aster.amo.molang.ide.schema.MoLangSchemaService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MoLangCompletionContributor extends CompletionContributor {

    private static final Set<String> KEYWORDS = Set.of(
            "fn", "if", "else", "switch", "while", "struct", "import",
            "return", "break", "continue", "for", "default", "true", "false"
    );

    private static final Pattern PREFIX_CHAIN_PATTERN = Pattern.compile(
            "(?:^|[^a-zA-Z0-9_])" +
            "(q|query|v|variable|t|temp|f|function|c|context|math)" +
            "((?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*)" +
            "\\.$"
    );

    private static final Pattern BARE_IDENT_PATTERN = Pattern.compile(
            "(?:^|[^a-zA-Z0-9_.])([a-zA-Z_][a-zA-Z0-9_]*)$"
    );

    private static final Pattern CONTEXT_ANNOTATION_PATTERN = Pattern.compile(
            "//\\s*@context\\s+(\\S*)$"
    );

    public MoLangCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(MoLangLanguage.INSTANCE),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        Project project = parameters.getPosition().getProject();
                        MoLangSchemaService schema = project.getService(MoLangSchemaService.class);
                        if (schema == null || !schema.isLoaded()) return;

                        Document doc = parameters.getEditor().getDocument();
                        int offset = parameters.getOffset();
                        String textBefore = getTextBefore(doc, offset);

                        String docText = doc.getText();
                        String runtimeName = schema.inferRuntimeFromContent(docText);
                        if (runtimeName == null) {
                            VirtualFile vFile = parameters.getOriginalFile().getVirtualFile();
                            runtimeName = vFile != null ? schema.inferRuntimeFromPath(vFile.getPath()) : null;
                        }

                        Matcher ctxMatcher = CONTEXT_ANNOTATION_PATTERN.matcher(textBefore);
                        if (ctxMatcher.find()) {
                            for (String name : schema.getRuntimeNames()) {
                                result.addElement(prioritize(
                                        LookupElementBuilder.create(name)
                                                .withIcon(AllIcons.Nodes.Tag)
                                                .withTypeText("runtime context"),
                                        200
                                ));
                            }
                            result.stopHere();
                            return;
                        }

                        Matcher chainMatcher = PREFIX_CHAIN_PATTERN.matcher(textBefore);
                        if (chainMatcher.find()) {
                            String prefix = normalizePrefix(chainMatcher.group(1));
                            String chainStr = chainMatcher.group(2);
                            String[] chain = chainStr.isEmpty()
                                    ? new String[0]
                                    : chainStr.substring(1).split("\\.");

                            handleChainCompletion(schema, result, prefix, chain, runtimeName, doc, offset);
                            result.stopHere();
                            return;
                        }

                        Matcher bareMatcher = BARE_IDENT_PATTERN.matcher(textBefore);
                        if (bareMatcher.find()) {
                            String partial = bareMatcher.group(1);
                            handleBareCompletion(result, partial);
                            return;
                        }
                    }
                });
    }

    private void handleChainCompletion(MoLangSchemaService schema,
                                       CompletionResultSet result,
                                       String prefix,
                                       String[] chain,
                                       @Nullable String runtimeName,
                                       Document doc,
                                       int offset) {
        switch (prefix) {
            case "q" -> handleQueryCompletion(schema, result, chain, runtimeName);
            case "math" -> handleMathCompletion(schema, result, chain);
            case "t" -> handleTempCompletion(result, doc);
            case "v" -> handleVariableCompletion(result, doc);
            case "f" -> handleFunctionCompletion(result, doc);
            case "c" -> handleContextCompletion(schema, result, runtimeName);
        }
    }

    private void handleQueryCompletion(MoLangSchemaService schema,
                                       CompletionResultSet result,
                                       String[] chain,
                                       @Nullable String runtimeName) {
        if (chain.length == 0) {
            Map<String, JsonObject> queryVars = schema.getQueryVariables(runtimeName);
            for (var entry : queryVars.entrySet()) {
                String name = entry.getKey();
                JsonObject obj = entry.getValue();
                String type = getStringField(obj, "type");
                String desc = getStringField(obj, "description");
                String structType = getStringField(obj, "struct_type");

                LookupElementBuilder builder = LookupElementBuilder.create(name)
                        .withIcon(getIconForType(type))
                        .withTypeText(type != null ? type : "")
                        .withTailText(structType != null ? " (" + structType + ")" : "", true);
                if (desc != null) {
                    builder = builder.withPresentableText(name);
                }
                result.addElement(prioritize(builder, "Struct".equals(type) ? 200 : 100));
            }

            addFunctionMapToResult(schema.getGeneralFunctions(), result, 50);
            return;
        }

        resolveAndAddCompletions(schema, result, runtimeName, chain);
    }

    private void handleMathCompletion(MoLangSchemaService schema,
                                      CompletionResultSet result,
                                      String[] chain) {
        if (chain.length == 0) {
            Map<String, JsonObject> mathFuncs = schema.getMathFunctions();
            addFunctionMapToResult(mathFuncs, result, 100);
        }
    }

    private void handleTempCompletion(CompletionResultSet result, Document doc) {
        Set<String> names = scanPrefixUsages(doc.getText(), "t", "temp");
        for (String name : names) {
            result.addElement(prioritize(
                    LookupElementBuilder.create(name)
                            .withIcon(AllIcons.Nodes.Variable)
                            .withTypeText("Temp"),
                    100
            ));
        }
    }

    private void handleVariableCompletion(CompletionResultSet result, Document doc) {
        Set<String> names = scanPrefixUsages(doc.getText(), "v", "variable");
        for (String name : names) {
            result.addElement(prioritize(
                    LookupElementBuilder.create(name)
                            .withIcon(AllIcons.Nodes.Field)
                            .withTypeText("Variable"),
                    100
            ));
        }
    }

    private void handleFunctionCompletion(CompletionResultSet result, Document doc) {
        Set<String> fnNames = scanFnDefinitions(doc.getText());
        for (String name : fnNames) {
            result.addElement(prioritize(
                    LookupElementBuilder.create(name)
                            .withIcon(AllIcons.Nodes.Function)
                            .withTypeText("fn()")
                            .withInsertHandler((ctx, item) -> {
                                ctx.getDocument().insertString(ctx.getTailOffset(), "()");
                                ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                            }),
                    100
            ));
        }
    }

    private void handleContextCompletion(MoLangSchemaService schema,
                                         CompletionResultSet result,
                                         @Nullable String runtimeName) {
        if (runtimeName != null) {
            Map<String, JsonObject> queryVars = schema.getRuntimeQueryVariables(runtimeName);
            for (var entry : queryVars.entrySet()) {
                result.addElement(prioritize(
                        LookupElementBuilder.create(entry.getKey())
                                .withIcon(AllIcons.Nodes.Property)
                                .withTypeText("Context"),
                        100
                ));
            }
        }
    }

    private void resolveAndAddCompletions(MoLangSchemaService schema,
                                          CompletionResultSet result,
                                          @Nullable String runtimeName,
                                          String[] chain) {
        MoLangSchemaService.SchemaResolution resolution = schema.resolveChain(runtimeName, chain);
        if (resolution != null) {
            addFunctionMapToResult(resolution.functions(), result, 100);
        }
    }

    private void addFunctionMapToResult(Map<String, JsonObject> functions,
                                        CompletionResultSet result,
                                        int basePriority) {
        for (var entry : functions.entrySet()) {
            String name = entry.getKey();
            JsonObject func = entry.getValue();
            String type = getStringField(func, "type");
            String returns = getStringField(func, "returns");
            String desc = getStringField(func, "description");
            String source = getStringField(func, "source");
            String returnType = returns != null ? returns : (type != null ? type : "");

            String paramSig = buildParamSignature(func);
            boolean hasParams = !paramSig.isEmpty();

            LookupElementBuilder builder = LookupElementBuilder.create(name)
                    .withIcon(getIconForType(type))
                    .withTypeText(returnType, true)
                    .withTailText(hasParams ? "(" + paramSig + ")" : "", true);

            if (hasParams) {
                builder = builder.withInsertHandler((ctx, item) -> {
                    ctx.getDocument().insertString(ctx.getTailOffset(), "()");
                    ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() - 1);
                });
            }

            result.addElement(prioritize(builder, basePriority));
        }
    }

    private void handleBareCompletion(CompletionResultSet result, String partial) {
        for (String kw : KEYWORDS) {
            result.addElement(prioritize(
                    LookupElementBuilder.create(kw)
                            .withIcon(AllIcons.Nodes.AbstractClass)
                            .withTypeText("keyword")
                            .bold(),
                    200
            ));
        }
        for (String prefix : List.of("q", "v", "t", "f", "c", "math")) {
            result.addElement(prioritize(
                    LookupElementBuilder.create(prefix)
                            .withIcon(AllIcons.Nodes.Tag)
                            .withTypeText("prefix")
                            .withInsertHandler((ctx, item) -> {
                                ctx.getDocument().insertString(ctx.getTailOffset(), ".");
                                ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset());
                            }),
                    150
            ));
        }
    }

    private static String getTextBefore(Document doc, int offset) {
        int lineNum = doc.getLineNumber(offset);
        int lineStart = doc.getLineStartOffset(lineNum);
        return doc.getText().substring(lineStart, offset);
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

    private static Set<String> scanPrefixUsages(String text, String shortPrefix, String longPrefix) {
        Set<String> names = new LinkedHashSet<>();
        Pattern p = Pattern.compile("(?:" + Pattern.quote(shortPrefix) + "|" + Pattern.quote(longPrefix) + ")\\.([a-zA-Z_][a-zA-Z0-9_]*)");
        Matcher m = p.matcher(text);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static Set<String> scanFnDefinitions(String text) {
        Set<String> names = new LinkedHashSet<>();
        Pattern p = Pattern.compile("fn\\s*\\(\\s*'([^']+)'");
        Matcher m = p.matcher(text);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static Icon getIconForType(@Nullable String type) {
        if (type == null) return AllIcons.Nodes.Property;
        return switch (type) {
            case "Number" -> AllIcons.Nodes.Field;
            case "String" -> AllIcons.Nodes.Variable;
            case "Struct" -> AllIcons.Nodes.Class;
            case "Unit", "Void" -> AllIcons.Nodes.Method;
            default -> AllIcons.Nodes.Function;
        };
    }

    private static LookupElement prioritize(LookupElementBuilder builder, int priority) {
        return PrioritizedLookupElement.withPriority(builder, priority);
    }

    @Nullable
    private static String getStringField(JsonObject obj, String field) {
        if (obj == null || !obj.has(field)) return null;
        JsonElement el = obj.get(field);
        return el.isJsonPrimitive() ? el.getAsString() : null;
    }
}

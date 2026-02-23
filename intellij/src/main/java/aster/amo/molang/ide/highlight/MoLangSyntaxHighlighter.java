package aster.amo.molang.ide.highlight;

import aster.amo.molang.ide.lexer.MoLangLexer;
import aster.amo.molang.ide.lexer.MoLangTokenTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class MoLangSyntaxHighlighter extends SyntaxHighlighterBase {

    public static final TextAttributesKey LINE_COMMENT =
            createTextAttributesKey("MOLANG_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey BLOCK_COMMENT =
            createTextAttributesKey("MOLANG_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    public static final TextAttributesKey KEYWORD =
            createTextAttributesKey("MOLANG_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey NUMBER =
            createTextAttributesKey("MOLANG_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey STRING =
            createTextAttributesKey("MOLANG_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey BOOLEAN =
            createTextAttributesKey("MOLANG_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey IDENTIFIER =
            createTextAttributesKey("MOLANG_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey PREFIX_QUERY =
            createTextAttributesKey("MOLANG_PREFIX_QUERY", DefaultLanguageHighlighterColors.CONSTANT);
    public static final TextAttributesKey PREFIX_VARIABLE =
            createTextAttributesKey("MOLANG_PREFIX_VARIABLE", DefaultLanguageHighlighterColors.GLOBAL_VARIABLE);
    public static final TextAttributesKey PREFIX_TEMP =
            createTextAttributesKey("MOLANG_PREFIX_TEMP", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
    public static final TextAttributesKey PREFIX_FUNCTION =
            createTextAttributesKey("MOLANG_PREFIX_FUNCTION", DefaultLanguageHighlighterColors.FUNCTION_CALL);
    public static final TextAttributesKey PREFIX_CONTEXT =
            createTextAttributesKey("MOLANG_PREFIX_CONTEXT", DefaultLanguageHighlighterColors.METADATA);
    public static final TextAttributesKey PREFIX_MATH =
            createTextAttributesKey("MOLANG_PREFIX_MATH", DefaultLanguageHighlighterColors.STATIC_METHOD);
    public static final TextAttributesKey DOT =
            createTextAttributesKey("MOLANG_DOT", DefaultLanguageHighlighterColors.DOT);
    public static final TextAttributesKey OPERATOR =
            createTextAttributesKey("MOLANG_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    public static final TextAttributesKey PARENTHESES =
            createTextAttributesKey("MOLANG_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES);
    public static final TextAttributesKey BRACES =
            createTextAttributesKey("MOLANG_BRACES", DefaultLanguageHighlighterColors.BRACES);
    public static final TextAttributesKey BRACKETS =
            createTextAttributesKey("MOLANG_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey SEMICOLON =
            createTextAttributesKey("MOLANG_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON);
    public static final TextAttributesKey COMMA =
            createTextAttributesKey("MOLANG_COMMA", DefaultLanguageHighlighterColors.COMMA);
    public static final TextAttributesKey BAD_CHARACTER =
            createTextAttributesKey("MOLANG_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);

    private static final Map<IElementType, TextAttributesKey[]> TOKEN_MAP = new HashMap<>();

    static {
        TOKEN_MAP.put(MoLangTokenTypes.LINE_COMMENT, keys(LINE_COMMENT));
        TOKEN_MAP.put(MoLangTokenTypes.BLOCK_COMMENT, keys(BLOCK_COMMENT));
        TOKEN_MAP.put(MoLangTokenTypes.KEYWORD, keys(KEYWORD));
        TOKEN_MAP.put(MoLangTokenTypes.NUMBER, keys(NUMBER));
        TOKEN_MAP.put(MoLangTokenTypes.STRING, keys(STRING));
        TOKEN_MAP.put(MoLangTokenTypes.BOOLEAN, keys(BOOLEAN));
        TOKEN_MAP.put(MoLangTokenTypes.IDENTIFIER, keys(IDENTIFIER));
        TOKEN_MAP.put(MoLangTokenTypes.PREFIX_Q, keys(PREFIX_QUERY));
        TOKEN_MAP.put(MoLangTokenTypes.PREFIX_V, keys(PREFIX_VARIABLE));
        TOKEN_MAP.put(MoLangTokenTypes.PREFIX_T, keys(PREFIX_TEMP));
        TOKEN_MAP.put(MoLangTokenTypes.PREFIX_F, keys(PREFIX_FUNCTION));
        TOKEN_MAP.put(MoLangTokenTypes.PREFIX_C, keys(PREFIX_CONTEXT));
        TOKEN_MAP.put(MoLangTokenTypes.PREFIX_MATH, keys(PREFIX_MATH));
        TOKEN_MAP.put(MoLangTokenTypes.DOT, keys(DOT));
        TOKEN_MAP.put(MoLangTokenTypes.OPERATOR, keys(OPERATOR));
        TOKEN_MAP.put(MoLangTokenTypes.COMPOUND_OP, keys(OPERATOR));
        TOKEN_MAP.put(MoLangTokenTypes.ARROW, keys(OPERATOR));
        TOKEN_MAP.put(MoLangTokenTypes.LPAREN, keys(PARENTHESES));
        TOKEN_MAP.put(MoLangTokenTypes.RPAREN, keys(PARENTHESES));
        TOKEN_MAP.put(MoLangTokenTypes.LBRACE, keys(BRACES));
        TOKEN_MAP.put(MoLangTokenTypes.RBRACE, keys(BRACES));
        TOKEN_MAP.put(MoLangTokenTypes.LBRACKET, keys(BRACKETS));
        TOKEN_MAP.put(MoLangTokenTypes.RBRACKET, keys(BRACKETS));
        TOKEN_MAP.put(MoLangTokenTypes.SEMICOLON, keys(SEMICOLON));
        TOKEN_MAP.put(MoLangTokenTypes.COMMA, keys(COMMA));
        TOKEN_MAP.put(MoLangTokenTypes.QUESTION, keys(OPERATOR));
        TOKEN_MAP.put(MoLangTokenTypes.COLON, keys(OPERATOR));
        TOKEN_MAP.put(MoLangTokenTypes.BAD_CHARACTER, keys(BAD_CHARACTER));
    }

    private static TextAttributesKey[] keys(TextAttributesKey key) {
        return new TextAttributesKey[]{key};
    }

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new MoLangLexer();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        TextAttributesKey[] keys = TOKEN_MAP.get(tokenType);
        return keys != null ? keys : TextAttributesKey.EMPTY_ARRAY;
    }
}

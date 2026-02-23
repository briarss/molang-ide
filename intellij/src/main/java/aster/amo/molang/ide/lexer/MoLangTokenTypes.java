package aster.amo.molang.ide.lexer;

import aster.amo.molang.ide.MoLangLanguage;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class MoLangTokenTypes {
    public static final IElementType LINE_COMMENT = new MoLangTokenType("LINE_COMMENT");
    public static final IElementType BLOCK_COMMENT = new MoLangTokenType("BLOCK_COMMENT");

    public static final IElementType NUMBER = new MoLangTokenType("NUMBER");
    public static final IElementType STRING = new MoLangTokenType("STRING");
    public static final IElementType BOOLEAN = new MoLangTokenType("BOOLEAN");

    public static final IElementType KEYWORD = new MoLangTokenType("KEYWORD");

    public static final IElementType IDENTIFIER = new MoLangTokenType("IDENTIFIER");

    public static final IElementType PREFIX_Q = new MoLangTokenType("PREFIX_Q");
    public static final IElementType PREFIX_V = new MoLangTokenType("PREFIX_V");
    public static final IElementType PREFIX_T = new MoLangTokenType("PREFIX_T");
    public static final IElementType PREFIX_F = new MoLangTokenType("PREFIX_F");
    public static final IElementType PREFIX_C = new MoLangTokenType("PREFIX_C");
    public static final IElementType PREFIX_MATH = new MoLangTokenType("PREFIX_MATH");

    public static final IElementType DOT = new MoLangTokenType("DOT");
    public static final IElementType LPAREN = new MoLangTokenType("LPAREN");
    public static final IElementType RPAREN = new MoLangTokenType("RPAREN");
    public static final IElementType LBRACE = new MoLangTokenType("LBRACE");
    public static final IElementType RBRACE = new MoLangTokenType("RBRACE");
    public static final IElementType LBRACKET = new MoLangTokenType("LBRACKET");
    public static final IElementType RBRACKET = new MoLangTokenType("RBRACKET");
    public static final IElementType SEMICOLON = new MoLangTokenType("SEMICOLON");
    public static final IElementType COMMA = new MoLangTokenType("COMMA");
    public static final IElementType QUESTION = new MoLangTokenType("QUESTION");
    public static final IElementType COLON = new MoLangTokenType("COLON");

    public static final IElementType OPERATOR = new MoLangTokenType("OPERATOR");
    public static final IElementType COMPOUND_OP = new MoLangTokenType("COMPOUND_OP");
    public static final IElementType ARROW = new MoLangTokenType("ARROW");

    public static final IElementType WHITE_SPACE = new MoLangTokenType("WHITE_SPACE");
    public static final IElementType BAD_CHARACTER = new MoLangTokenType("BAD_CHARACTER");

    public static final TokenSet COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT);
    public static final TokenSet WHITESPACES = TokenSet.create(WHITE_SPACE);
    public static final TokenSet STRINGS = TokenSet.create(STRING);

    private static class MoLangTokenType extends IElementType {
        MoLangTokenType(String debugName) {
            super(debugName, MoLangLanguage.INSTANCE);
        }
    }
}

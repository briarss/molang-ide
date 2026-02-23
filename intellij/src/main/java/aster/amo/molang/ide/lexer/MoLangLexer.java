package aster.amo.molang.ide.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class MoLangLexer extends LexerBase {
    private static final Set<String> KEYWORDS = Set.of(
            "fn", "if", "else", "switch", "while", "struct", "import",
            "return", "break", "continue", "for", "default"
    );

    private CharSequence buffer;
    private int bufferEnd;
    private int tokenStart;
    private int tokenEnd;
    private IElementType tokenType;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.bufferEnd = endOffset;
        this.tokenStart = startOffset;
        this.tokenEnd = startOffset;
        this.tokenType = null;
        advance();
    }

    @Override
    public int getState() {
        return 0;
    }

    @Nullable
    @Override
    public IElementType getTokenType() {
        return tokenType;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return bufferEnd;
    }

    @Override
    public void advance() {
        tokenStart = tokenEnd;
        if (tokenStart >= bufferEnd) {
            tokenType = null;
            return;
        }

        char c = buffer.charAt(tokenStart);

        if (Character.isWhitespace(c)) {
            tokenEnd = tokenStart + 1;
            while (tokenEnd < bufferEnd && Character.isWhitespace(buffer.charAt(tokenEnd))) {
                tokenEnd++;
            }
            tokenType = MoLangTokenTypes.WHITE_SPACE;
            return;
        }

        if (c == '/' && tokenStart + 1 < bufferEnd) {
            char next = buffer.charAt(tokenStart + 1);
            if (next == '/') {
                tokenEnd = tokenStart + 2;
                while (tokenEnd < bufferEnd && buffer.charAt(tokenEnd) != '\n') {
                    tokenEnd++;
                }
                tokenType = MoLangTokenTypes.LINE_COMMENT;
                return;
            }
            if (next == '*') {
                tokenEnd = tokenStart + 2;
                while (tokenEnd + 1 < bufferEnd) {
                    if (buffer.charAt(tokenEnd) == '*' && buffer.charAt(tokenEnd + 1) == '/') {
                        tokenEnd += 2;
                        tokenType = MoLangTokenTypes.BLOCK_COMMENT;
                        return;
                    }
                    tokenEnd++;
                }
                tokenEnd = bufferEnd;
                tokenType = MoLangTokenTypes.BLOCK_COMMENT;
                return;
            }
        }

        if (c == '\'') {
            tokenEnd = tokenStart + 1;
            while (tokenEnd < bufferEnd) {
                char sc = buffer.charAt(tokenEnd);
                if (sc == '\\' && tokenEnd + 1 < bufferEnd) {
                    tokenEnd += 2;
                    continue;
                }
                if (sc == '\'') {
                    tokenEnd++;
                    break;
                }
                tokenEnd++;
            }
            tokenType = MoLangTokenTypes.STRING;
            return;
        }

        if (Character.isDigit(c) || (c == '.' && tokenStart + 1 < bufferEnd && Character.isDigit(buffer.charAt(tokenStart + 1)))) {
            tokenEnd = tokenStart;
            if (c == '.') {
                tokenEnd++;
            } else {
                while (tokenEnd < bufferEnd && Character.isDigit(buffer.charAt(tokenEnd))) {
                    tokenEnd++;
                }
                if (tokenEnd < bufferEnd && buffer.charAt(tokenEnd) == '.') {
                    tokenEnd++;
                }
            }
            while (tokenEnd < bufferEnd && Character.isDigit(buffer.charAt(tokenEnd))) {
                tokenEnd++;
            }
            tokenType = MoLangTokenTypes.NUMBER;
            return;
        }

        if (Character.isLetter(c) || c == '_') {
            tokenEnd = tokenStart + 1;
            while (tokenEnd < bufferEnd && isIdentChar(buffer.charAt(tokenEnd))) {
                tokenEnd++;
            }
            String word = buffer.subSequence(tokenStart, tokenEnd).toString();

            boolean followedByDot = tokenEnd < bufferEnd && buffer.charAt(tokenEnd) == '.';

            if (followedByDot) {
                IElementType prefixType = getPrefixType(word);
                if (prefixType != null) {
                    tokenType = prefixType;
                    return;
                }
            }

            if (word.equals("true") || word.equals("false")) {
                tokenType = MoLangTokenTypes.BOOLEAN;
                return;
            }

            if (KEYWORDS.contains(word)) {
                tokenType = MoLangTokenTypes.KEYWORD;
                return;
            }

            tokenType = MoLangTokenTypes.IDENTIFIER;
            return;
        }

        if (tokenStart + 1 < bufferEnd) {
            char next = buffer.charAt(tokenStart + 1);
            String two = "" + c + next;
            switch (two) {
                case "->":
                    tokenEnd = tokenStart + 2;
                    tokenType = MoLangTokenTypes.ARROW;
                    return;
                case "+=":
                case "-=":
                case "*=":
                case "/=":
                case "==":
                case "!=":
                case ">=":
                case "<=":
                case "&&":
                case "||":
                case "??":
                    tokenEnd = tokenStart + 2;
                    tokenType = MoLangTokenTypes.COMPOUND_OP;
                    return;
            }
        }

        switch (c) {
            case '.':
                tokenEnd = tokenStart + 1;
                tokenType = MoLangTokenTypes.DOT;
                return;
            case '(':
                tokenEnd = tokenStart + 1;
                tokenType = MoLangTokenTypes.LPAREN;
                return;
            case ')':
                tokenEnd = tokenStart + 1;
                tokenType = MoLangTokenTypes.RPAREN;
                return;
            case '{':
                tokenEnd = tokenStart + 1;
                tokenType = MoLangTokenTypes.LBRACE;
                return;
            case '}':
                tokenEnd = tokenStart + 1;
                tokenType = MoLangTokenTypes.RBRACE;
                return;
            case '[':
                tokenEnd = tokenStart + 1;
                tokenType = MoLangTokenTypes.LBRACKET;
                return;
            case ']':
                tokenEnd = tokenStart + 1;
                tokenType = MoLangTokenTypes.RBRACKET;
                return;
            case ';':
                tokenEnd = tokenStart + 1;
                tokenType = MoLangTokenTypes.SEMICOLON;
                return;
            case ',':
                tokenEnd = tokenStart + 1;
                tokenType = MoLangTokenTypes.COMMA;
                return;
            case '?':
                tokenEnd = tokenStart + 1;
                tokenType = MoLangTokenTypes.QUESTION;
                return;
            case ':':
                tokenEnd = tokenStart + 1;
                tokenType = MoLangTokenTypes.COLON;
                return;
            case '+':
            case '-':
            case '*':
            case '/':
            case '=':
            case '!':
            case '<':
            case '>':
            case '%':
                tokenEnd = tokenStart + 1;
                tokenType = MoLangTokenTypes.OPERATOR;
                return;
        }

        tokenEnd = tokenStart + 1;
        tokenType = MoLangTokenTypes.BAD_CHARACTER;
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    @Nullable
    private static IElementType getPrefixType(String word) {
        return switch (word) {
            case "q", "query" -> MoLangTokenTypes.PREFIX_Q;
            case "v", "variable" -> MoLangTokenTypes.PREFIX_V;
            case "t", "temp" -> MoLangTokenTypes.PREFIX_T;
            case "f", "function" -> MoLangTokenTypes.PREFIX_F;
            case "c", "context" -> MoLangTokenTypes.PREFIX_C;
            case "math" -> MoLangTokenTypes.PREFIX_MATH;
            default -> null;
        };
    }
}

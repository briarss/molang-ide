package aster.amo.molang.ide.parser;

import aster.amo.molang.ide.MoLangLanguage;
import aster.amo.molang.ide.lexer.MoLangLexer;
import aster.amo.molang.ide.lexer.MoLangTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiBuilder;
import org.jetbrains.annotations.NotNull;

public class MoLangParserDefinition implements ParserDefinition {
    private static final IFileElementType FILE = new IFileElementType(MoLangLanguage.INSTANCE);

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new MoLangLexer();
    }

    @NotNull
    @Override
    public PsiParser createParser(Project project) {
        return (root, builder) -> {
            PsiBuilder.Marker rootMarker = builder.mark();
            while (!builder.eof()) {
                builder.advanceLexer();
            }
            rootMarker.done(root);
            return builder.getTreeBuilt();
        };
    }

    @NotNull
    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return MoLangTokenTypes.COMMENTS;
    }

    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return MoLangTokenTypes.STRINGS;
    }

    @NotNull
    @Override
    public TokenSet getWhitespaceTokens() {
        return MoLangTokenTypes.WHITESPACES;
    }

    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        IElementType type = node.getElementType();
        throw new UnsupportedOperationException("Unknown element type: " + type);
    }

    @NotNull
    @Override
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new MoLangFile(viewProvider);
    }
}

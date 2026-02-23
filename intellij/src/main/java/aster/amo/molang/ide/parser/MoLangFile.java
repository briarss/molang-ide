package aster.amo.molang.ide.parser;

import aster.amo.molang.ide.MoLangFileType;
import aster.amo.molang.ide.MoLangLanguage;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public class MoLangFile extends PsiFileBase {
    public MoLangFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, MoLangLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return MoLangFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "MoLang File";
    }
}

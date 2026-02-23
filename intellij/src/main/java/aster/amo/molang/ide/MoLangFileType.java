package aster.amo.molang.ide;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MoLangFileType extends LanguageFileType {
    public static final MoLangFileType INSTANCE = new MoLangFileType();

    private MoLangFileType() {
        super(MoLangLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "MoLang";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "MoLang script file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "molang";
    }

    @Override
    public Icon getIcon() {
        return MoLangIcons.FILE;
    }
}

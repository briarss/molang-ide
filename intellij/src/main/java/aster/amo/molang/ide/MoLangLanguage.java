package aster.amo.molang.ide;

import com.intellij.lang.Language;

public class MoLangLanguage extends Language {
    public static final MoLangLanguage INSTANCE = new MoLangLanguage();

    private MoLangLanguage() {
        super("MoLang");
    }
}

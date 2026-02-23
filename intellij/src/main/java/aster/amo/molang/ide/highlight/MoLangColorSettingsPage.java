package aster.amo.molang.ide.highlight;

import aster.amo.molang.ide.MoLangIcons;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class MoLangColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
            new AttributesDescriptor("Comments//Line comment", MoLangSyntaxHighlighter.LINE_COMMENT),
            new AttributesDescriptor("Comments//Block comment", MoLangSyntaxHighlighter.BLOCK_COMMENT),
            new AttributesDescriptor("Keywords", MoLangSyntaxHighlighter.KEYWORD),
            new AttributesDescriptor("Numbers", MoLangSyntaxHighlighter.NUMBER),
            new AttributesDescriptor("Strings", MoLangSyntaxHighlighter.STRING),
            new AttributesDescriptor("Booleans", MoLangSyntaxHighlighter.BOOLEAN),
            new AttributesDescriptor("Identifiers", MoLangSyntaxHighlighter.IDENTIFIER),
            new AttributesDescriptor("Prefixes//Query (q.)", MoLangSyntaxHighlighter.PREFIX_QUERY),
            new AttributesDescriptor("Prefixes//Variable (v.)", MoLangSyntaxHighlighter.PREFIX_VARIABLE),
            new AttributesDescriptor("Prefixes//Temp (t.)", MoLangSyntaxHighlighter.PREFIX_TEMP),
            new AttributesDescriptor("Prefixes//Function (f.)", MoLangSyntaxHighlighter.PREFIX_FUNCTION),
            new AttributesDescriptor("Prefixes//Context (c.)", MoLangSyntaxHighlighter.PREFIX_CONTEXT),
            new AttributesDescriptor("Prefixes//Math", MoLangSyntaxHighlighter.PREFIX_MATH),
            new AttributesDescriptor("Operators", MoLangSyntaxHighlighter.OPERATOR),
            new AttributesDescriptor("Parentheses", MoLangSyntaxHighlighter.PARENTHESES),
            new AttributesDescriptor("Braces", MoLangSyntaxHighlighter.BRACES),
            new AttributesDescriptor("Brackets", MoLangSyntaxHighlighter.BRACKETS),
            new AttributesDescriptor("Semicolons", MoLangSyntaxHighlighter.SEMICOLON),
            new AttributesDescriptor("Commas", MoLangSyntaxHighlighter.COMMA),
    };

    @Nullable
    @Override
    public Icon getIcon() {
        return MoLangIcons.FILE;
    }

    @NotNull
    @Override
    public SyntaxHighlighter getHighlighter() {
        return new MoLangSyntaxHighlighter();
    }

    @NotNull
    @Override
    public String getDemoText() {
        return """
                // MoLang Example
                /* Block comment */
                fn('heal', (amount) -> {
                    v.health = q.pokemon.current_hp + amount;
                    if (v.health > q.pokemon.max_hp) {
                        v.health = q.pokemon.max_hp;
                    };
                    q.pokemon.set_hp(v.health);
                    return v.health;
                });

                t.result = math.floor(q.pokemon.level * 1.5);
                switch (q.pokemon.species.identifier) {
                    'bulbasaur' : t.result = t.result + 10;
                    default : t.result = t.result + 5;
                };

                import('cobblemon:util/math_helpers');
                f.heal(50);
                """;
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "MoLang";
    }
}

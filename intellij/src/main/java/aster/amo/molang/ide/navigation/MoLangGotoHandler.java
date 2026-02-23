package aster.amo.molang.ide.navigation;

import aster.amo.molang.ide.MoLangFileType;
import aster.amo.molang.ide.MoLangLanguage;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MoLangGotoHandler implements GotoDeclarationHandler {

    private static final Pattern FN_CALL_PATTERN = Pattern.compile(
            "(?:f|function)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\("
    );

    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "import\\s*\\(\\s*'([^']+)'\\s*\\)"
    );

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement,
                                                              int offset,
                                                              Editor editor) {
        if (sourceElement == null) return null;
        PsiFile file = sourceElement.getContainingFile();
        if (file == null || file.getLanguage() != MoLangLanguage.INSTANCE) return null;

        Project project = sourceElement.getProject();
        Document doc = editor.getDocument();
        String fullText = doc.getText();

        // Try to resolve f.xxx() function calls
        PsiElement fnTarget = resolveFunctionCall(project, fullText, offset);
        if (fnTarget != null) return new PsiElement[]{fnTarget};

        // Try to resolve import('namespace:path')
        PsiElement importTarget = resolveImport(project, fullText, offset);
        if (importTarget != null) return new PsiElement[]{importTarget};

        return null;
    }

    @Nullable
    private PsiElement resolveFunctionCall(Project project, String text, int offset) {
        // Find if the caret is within a f.xxx() call
        String fnName = findFunctionNameAtOffset(text, offset);
        if (fnName == null) return null;

        // Search the function index
        FileBasedIndex index = FileBasedIndex.getInstance();
        Collection<VirtualFile> files = FileTypeIndex.getFiles(
                MoLangFileType.INSTANCE,
                GlobalSearchScope.projectScope(project)
        );

        for (VirtualFile vFile : files) {
            List<List<Integer>> valueLists = index.getValues(MoLangFunctionIndex.NAME, fnName, GlobalSearchScope.fileScope(project, vFile));
            for (List<Integer> offsetList : valueLists) {
                for (int fnOffset : offsetList) {
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
                    if (psiFile != null) {
                        PsiElement element = psiFile.findElementAt(fnOffset);
                        if (element != null) return element;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private PsiElement resolveImport(Project project, String text, int offset) {
        // Check if caret is within an import('...') call
        String importPath = findImportPathAtOffset(text, offset);
        if (importPath == null) return null;

        // Parse namespace:path
        int colonIdx = importPath.indexOf(':');
        if (colonIdx < 0) return null;
        String namespace = importPath.substring(0, colonIdx);
        String path = importPath.substring(colonIdx + 1);

        // Resolve: data/{namespace}/molang/{path}.molang
        // Search project for matching file
        String targetRelPath = "data/" + namespace + "/molang/" + path + ".molang";

        // Search in project content roots
        Collection<VirtualFile> files = FileTypeIndex.getFiles(
                MoLangFileType.INSTANCE,
                GlobalSearchScope.projectScope(project)
        );

        for (VirtualFile vFile : files) {
            String vPath = vFile.getPath().replace('\\', '/');
            if (vPath.endsWith(targetRelPath) || vPath.contains("/" + targetRelPath)) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
                if (psiFile != null) return psiFile;
            }
        }

        return null;
    }

    @Nullable
    private String findFunctionNameAtOffset(String text, int offset) {
        // Search around offset for f.xxx( pattern
        int searchStart = Math.max(0, offset - 100);
        int searchEnd = Math.min(text.length(), offset + 50);
        String region = text.substring(searchStart, searchEnd);
        int relativeOffset = offset - searchStart;

        Matcher m = FN_CALL_PATTERN.matcher(region);
        while (m.find()) {
            if (m.start() <= relativeOffset && relativeOffset <= m.end()) {
                return m.group(1);
            }
        }
        return null;
    }

    @Nullable
    private String findImportPathAtOffset(String text, int offset) {
        int searchStart = Math.max(0, offset - 200);
        int searchEnd = Math.min(text.length(), offset + 100);
        String region = text.substring(searchStart, searchEnd);
        int relativeOffset = offset - searchStart;

        Matcher m = IMPORT_PATTERN.matcher(region);
        while (m.find()) {
            if (m.start() <= relativeOffset && relativeOffset <= m.end()) {
                return m.group(1);
            }
        }
        return null;
    }
}

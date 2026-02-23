package aster.amo.molang.ide.navigation;

import aster.amo.molang.ide.MoLangFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indexes fn('name', ...) definitions across all .molang files.
 * Maps function name → list of offsets within the file.
 */
public class MoLangFunctionIndex extends FileBasedIndexExtension<String, List<Integer>> {
    public static final ID<String, List<Integer>> NAME = ID.create("molang.function.index");

    private static final Pattern FN_PATTERN = Pattern.compile("fn\\s*\\(\\s*'([^']+)'");

    @NotNull
    @Override
    public ID<String, List<Integer>> getName() {
        return NAME;
    }

    @NotNull
    @Override
    public DataIndexer<String, List<Integer>, FileContent> getIndexer() {
        return inputData -> {
            Map<String, List<Integer>> result = new HashMap<>();
            String text = inputData.getContentAsText().toString();
            Matcher m = FN_PATTERN.matcher(text);
            while (m.find()) {
                String fnName = m.group(1);
                result.computeIfAbsent(fnName, k -> new ArrayList<>()).add(m.start());
            }
            return result;
        };
    }

    @NotNull
    @Override
    public KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public DataExternalizer<List<Integer>> getValueExternalizer() {
        return new DataExternalizer<>() {
            @Override
            public void save(@NotNull DataOutput out, List<Integer> value) throws IOException {
                out.writeInt(value.size());
                for (int offset : value) {
                    out.writeInt(offset);
                }
            }

            @Override
            public List<Integer> read(@NotNull DataInput in) throws IOException {
                int size = in.readInt();
                List<Integer> result = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    result.add(in.readInt());
                }
                return result;
            }
        };
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        return new DefaultFileTypeSpecificInputFilter(MoLangFileType.INSTANCE) {
            @Override
            public boolean acceptInput(@NotNull VirtualFile file) {
                return "molang".equals(file.getExtension());
            }
        };
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }
}

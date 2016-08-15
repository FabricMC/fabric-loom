package chorusmc.gradle.util.delayed;

import chorusmc.gradle.ChorusGradleExtension;

import java.io.File;
import java.util.function.Function;

public class DelayedFile implements IDelayed<File> {
    private File file;
    private Function<ChorusGradleExtension, File> function;

    public DelayedFile(Function<ChorusGradleExtension, File> function) {
        this.function = function;
    }

    @Override
    public File get(ChorusGradleExtension extension) {
        if (this.file == null) {
            this.file = this.function.apply(extension);
        }
        return this.file;
    }
}

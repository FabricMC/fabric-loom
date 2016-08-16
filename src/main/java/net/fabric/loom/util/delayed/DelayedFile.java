package net.fabric.loom.util.delayed;

import net.fabric.loom.LoomGradleExtension;

import java.io.File;
import java.util.function.Function;

public class DelayedFile implements IDelayed<File> {
    private File file;
    private Function<LoomGradleExtension, File> function;

    public DelayedFile(Function<LoomGradleExtension, File> function) {
        this.function = function;
    }

    @Override
    public File get(LoomGradleExtension extension) {
        if (this.file == null) {
            this.file = this.function.apply(extension);
        }
        return this.file;
    }
}

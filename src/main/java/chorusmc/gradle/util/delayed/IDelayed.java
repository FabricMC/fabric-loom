package chorusmc.gradle.util.delayed;

import chorusmc.gradle.ChorusGradleExtension;

public interface IDelayed<T> {
    T get(ChorusGradleExtension extension);
}

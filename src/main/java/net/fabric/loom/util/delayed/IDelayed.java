package net.fabric.loom.util.delayed;

import net.fabric.loom.LoomGradleExtension;

public interface IDelayed<T> {
    T get(LoomGradleExtension extension);
}

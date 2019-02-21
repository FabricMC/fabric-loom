/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.tasks.cache;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loom.util.Utils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

/**
 * TaskInputCache allows tasks to have execution time evaluated cached inputs.
 * All fields or method annotated with {@link CachedInput} or {@link Input} will
 * be used in up-to-date checking, TaskInputCache will first attempt to locate a getter
 * for any field, prefixing 'get', 'has' and 'is' to the field name, Getters are required
 * to have 0 method parameters.
 * TaskInputCache will also search for {@link CachedOutput} and {@link OutputFile} annotations,
 * if any of either are found, its considered a 'complex' cache, instead of a per task cache
 * it switches to a per output cache, storing a cache file next to each output (appending '.input_cache.json')
 * and each output must validate against all hashes for the task to be considered up-to-date, this is useful for
 * global cached files.
 *
 * TODO, Ability to override base directory for complex caches.
 *
 *
 * Created by covers1624 on 7/02/19.
 */
public class TaskInputCache {

    private static final Logger logger = Logging.getLogger("TaskInputCacheFactory");
    private static Map<Class<?>, TaskClass> cache = new HashMap<>();
    private static Gson gson = new GsonBuilder().registerTypeAdapterFactory(Utils.hashCodeStringTypeFactory).create();

    public static void bind(Task task) {
        TaskClass clazz = lookup(task.getClass());
        task.getOutputs().upToDateWhen(t -> {
            Map<String, HashCode> hashes = clazz.computeHashes(task);
            if (hashes == null) {
                return false;
            }
            if (clazz.isSimpleCache()) {
                CacheEntry entry = clazz.loadSimpleCache(t);
                return compare(hashes, entry.inputs);
            } else {
                boolean forcedSimple = task instanceof ICachedInputTask && ((ICachedInputTask) task).isSimpleCache();
                for (TaskClass.CachedProperty output : clazz.getOutputs()) {
                    File file = getComplexCache(output, task, forcedSimple);
                    if (file == null) {
                        return false;
                    }
                    HashCode outputHash;
                    try {
                        outputHash = output.computeHash(task);
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        task.getLogger().error("Exception hashing property '{}' on task '{}'.", output.getName(), task.getName(), e);
                        return false;
                    }
                    CacheEntry entry = clazz.loadCache(file);

                    if (!compare(hashes, entry.inputs) || !Objects.equals(outputHash, entry.output)) {
                        return false;
                    }
                }
                return true;
            }
        });
        task.doLast(t -> {
            CacheEntry entry = new CacheEntry();
            entry.inputs = clazz.computeHashes(t);
            if (entry.inputs == null) {
                t.getLogger().error("Unable to save hash cache for task '{}'.", t.getName());
                return;
            }
            if (clazz.isSimpleCache()) {
                Utils.toJson(gson, entry, CacheEntry.class, Utils.makeFile(getSimpleCache(t)));
            } else {
                boolean forcedSimple = task instanceof ICachedInputTask && ((ICachedInputTask) task).isSimpleCache();
                for (TaskClass.CachedProperty output : clazz.getOutputs()) {
                    File file = getComplexCache(output, t, forcedSimple);
                    if (file != null) {
                        try {
                            CacheEntry complexEntry = entry.withOutput(output.computeHash(task));
                            Utils.toJson(gson, complexEntry, CacheEntry.class, Utils.makeFile(file));
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            task.getLogger().error("Exception hashing property '{}' on task '{}'.", output.getName(), task.getName(), e);
                            return;
                        }
                    }
                }
            }

        });
    }

    private static TaskClass lookup(Class<?> clazz) {
        return cache.computeIfAbsent(clazz, TaskClass::new);
    }

    private static File getComplexCache(TaskClass.CachedProperty output, Task task, boolean forcedSimple) {
        Object obj;
        try {
            obj = output.get(task);
        } catch (InvocationTargetException | IllegalAccessException e) {
            task.getLogger().warn("Exception retrieving property '{}' on task '{}'.", output.getName(), task.getName(), e);
            return null;
        }
        if (!(obj instanceof File)) {
            task.getLogger().error("Value of property {} on task {} does not evaluate to 'File'", output.getName(), task.getName());
        }
        File file = ((File) obj).getAbsoluteFile();
        File dir = file.getParentFile();
        if (forcedSimple) {
            dir = new File(task.getProject().getBuildDir(), task.getName());
        }
        return new File(dir, file.getName() + ".input_cache.json");
    }

    private static File getSimpleCache(Task task) {
        return new File(task.getProject().getBuildDir(), task.getName() + "/input_cache.json");
    }

    private static boolean compare(Map<String, HashCode> map1, Map<String, HashCode> map2) {
        //fail fast on size.
        if (map1.size() != map2.size()) {
            return false;
        }
        //Check if the both contain all each others keys.
        if (!map1.keySet().containsAll(map2.keySet()) || !map2.keySet().containsAll(map1.keySet())) {
            return false;
        }
        for (Map.Entry<String, HashCode> e1 : map1.entrySet()) {
            if (!e1.getValue().equals(map2.get(e1.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static class CacheEntry {

        public HashCode output = null;
        public Map<String, HashCode> inputs = new HashMap<>();

        public CacheEntry withOutput(HashCode output) {
            CacheEntry entry = new CacheEntry();
            entry.output = output;
            entry.inputs.putAll(inputs);
            return entry;
        }

    }

    private static class TaskClass {

        private TaskClass superClass;
        private List<TaskClass> interfaces = new ArrayList<>();
        private Set<CachedProperty> inputs = new HashSet<>();
        private Set<CachedProperty> outputs = new HashSet<>();

        public TaskClass(Class<?> clazz) {
            Class<?> superClass = clazz.getSuperclass();
            //Recurse up superclass and interface hierarchy.
            if (superClass != null && !superClass.getName().startsWith("org.gradle")) {
                this.superClass = lookup(superClass);
                for (Class<?> iFace : superClass.getInterfaces()) {
                    interfaces.add(lookup(iFace));
                }
            }
            findAnnotated(clazz, CachedInput.class, inputs::add);
            findAnnotated(clazz, Input.class, inputs::add);
            findAnnotated(clazz, CachedOutput.class, outputs::add);
            findAnnotated(clazz, OutputFile.class, outputs::add);

            //Add all inherited properties.
            if (this.superClass != null) {
                inputs.addAll(this.superClass.getInputs());
                outputs.addAll(this.superClass.getOutputs());
            }
            for (TaskClass iFace : interfaces) {
                inputs.addAll(iFace.getInputs());
                outputs.addAll(iFace.getOutputs());
            }
        }

        public boolean isSimpleCache() {
            return getOutputs().isEmpty();
        }

        public CacheEntry loadSimpleCache(Task task) {
            return loadCache(getSimpleCache(task));
        }

        public CacheEntry loadCache(File cacheFile) {
            if (!cacheFile.exists()) {
                return new CacheEntry();
            }
            return Utils.fromJson(gson, cacheFile, CacheEntry.class);
        }

        public Set<CachedProperty> getInputs() {
            return inputs;
        }

        public Set<CachedProperty> getOutputs() {
            return outputs;
        }

        public Map<String, HashCode> computeHashes(Task task) {
            Map<String, HashCode> hashes = new HashMap<>();
            for (CachedProperty property : getInputs()) {
                try {
                    hashes.put(property.getName(), property.computeHash(task));
                } catch (InvocationTargetException | IllegalAccessException e) {
                    task.getLogger().error("Exception hashing property '{}' on task '{}'.", property.getName(), task.getName(), e);
                    return null;
                }
            }
            return hashes;
        }

        private static void findAnnotated(Class<?> clazz, Class<? extends Annotation> annotation, Consumer<CachedProperty> sink) {
            List<Method> methods = new ArrayList<>();
            List<Field> fields = new ArrayList<>();
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.isAnnotationPresent(annotation)) {
                    if (m.getParameterCount() != 0) {
                        logger.warn("Method '{}.{}' is annotated with '{}' but takes parameters.", clazz.getName(), m.getName(), annotation.getName());
                        continue;
                    }
                    m.setAccessible(true);
                    methods.add(m);
                }
            }
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(annotation)) {
                    Method getter = findGetter(clazz, field);
                    if (getter != null && !methods.contains(getter)) {
                        getter.setAccessible(true);
                        methods.add(getter);
                        continue;
                    }
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
            methods.stream().map(CachedProperty::new).forEach(sink);
            fields.stream().map(CachedProperty::new).forEach(sink);
        }

        private static Method findGetter(Class<?> clazz, Field field) {
            String upper = StringUtils.capitalize(field.getName());
            List<String> attempts = field.getType().equals(boolean.class) ? Arrays.asList("is", "get", "has") : Collections.singletonList("get");
            for (String attempt : attempts) {
                Method m = findMethod(clazz, attempt + upper);
                if (m == null) {
                    continue;
                }
                if (m.getParameterCount() != 0) {
                    String clazzName = clazz.getName();
                    logger.warn("Method '{}.{}' appears to be a getter for Field '{}.{}' but takes parameters?", clazzName, attempt, clazzName, field.getName());
                    continue;
                }
                return m;
            }
            return null;
        }

        private static Method findMethod(Class<?> clazz, String name) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(name)) {
                    return method;
                }
            }
            return null;
        }

        private static class CachedProperty {

            private final Method method;
            private final Field field;

            public CachedProperty(Field field) {
                this(null, field);
            }

            public CachedProperty(Method method) {
                this(method, null);
            }

            protected CachedProperty(Method method, Field field) {
                this.method = method;
                this.field = field;
            }

            public String getName() {
                if (method != null) {
                    return method.getName();
                }
                if (field != null) {
                    return field.getName();
                }
                //Impossible.
                throw new IllegalStateException("Method and Field null");
            }

            public Object get(Object instance) throws InvocationTargetException, IllegalAccessException {
                if (method != null) {
                    return method.invoke(instance);
                }
                if (field != null) {
                    return field.get(instance);
                }
                //Impossible.
                throw new IllegalStateException("Method and Field null");
            }

            public HashCode computeHash(Object instance) throws InvocationTargetException, IllegalAccessException {
                Hasher hasher = Hashing.sha256().newHasher();
                addToHasher(hasher, get(instance));
                return hasher.hash();
            }

            @Override
            public boolean equals(Object obj) {
                if (super.equals(obj)) {
                    return true;
                }
                if (!(obj instanceof CachedProperty)) {
                    return false;
                }
                CachedProperty other = (CachedProperty) obj;
                return equals(method, other.method) && equals(field, other.field);
            }

            private static boolean equals(Object a, Object b) {
                return (a == null && b == null) || Objects.equals(a, b);
            }

            //This _should_ suffice.
            @Override
            public int hashCode() {
                if (method != null) {
                    return method.hashCode() ^ 5454;
                }
                if (field != null) {
                    return field.hashCode() ^ 32323;
                }
                //Impossible
                return super.hashCode();
            }

            //This is likely cancer and may not cover all cases.
            private static void addToHasher(Hasher hasher, Object value) {
                if (value instanceof File) {
                    File file = (File) value;
                    if (file.isDirectory()) {
                        File[] files = file.listFiles();
                        if (files != null) {
                            for (File child : files) {
                                addToHasher(hasher, child);
                            }
                        }
                    } else if (file.isFile()) {
                        Utils.addToHasher(hasher, file);
                    }
                } else if (value instanceof CharSequence) {
                    hasher.putBytes(value.toString().getBytes());
                } else if (value instanceof Iterable) {
                    for (Object v2 : (Iterable) value) {
                        addToHasher(hasher, v2);
                    }
                } else if (value instanceof Object[]) {
                    for (Object v2 : (Object[]) value) {
                        addToHasher(hasher, v2);
                    }
                } else if (value instanceof PatternSet) {
                    PatternSet patternSet = (PatternSet) value;
                    hasher.putBoolean(patternSet.isCaseSensitive());
                    addToHasher(hasher, patternSet.getIncludes());
                    addToHasher(hasher, patternSet.getExcludes());
                } else if (value instanceof Boolean) {
                    hasher.putBoolean((Boolean) value);
                } else if (value instanceof Integer) {
                    hasher.putInt((Integer) value);
                } else if (value instanceof Long) {
                    hasher.putLong((Long) value);
                } else if (value instanceof Byte) {
                    hasher.putByte((Byte) value);
                } else if (value instanceof Character) {
                    hasher.putChar((Character) value);
                } else if (value instanceof Float) {
                    hasher.putFloat((Float) value);
                } else if (value instanceof Double) {
                    hasher.putDouble((Double) value);
                } else {
                    logger.warn("Type '{}' cannot be hashed. Unhandled type.", value.getClass());
                }
            }
        }
    }
}

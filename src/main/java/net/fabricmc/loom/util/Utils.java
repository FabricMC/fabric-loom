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

package net.fabricmc.loom.util;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import groovy.lang.Closure;
import net.fabricmc.stitch.util.StitchUtil;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.WillNotClose;
import java.io.*;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.google.gson.internal.bind.TypeAdapters.newFactory;

/**
 * Created by covers1624 on 5/02/19.
 */
@SuppressWarnings ("UnstableApiUsage")
public class Utils {

    //32k buffer.
    private static final ThreadLocal<byte[]> bufferCache = ThreadLocal.withInitial(() -> new byte[32 * 1024]);

    public static final TypeAdapterFactory lowerCaseEnumFactory = new LowerCaseEnumAdapterFactory();
    public static final TypeAdapterFactory fileStringTypeFactory = newFactory(File.class, new FileAdapter());
    public static final TypeAdapterFactory hashCodeStringTypeFactory = newFactory(HashCode.class, new HashCodeAdapter());

    public static final Gson gson = new GsonBuilder()//
            .registerTypeAdapter(File.class, new FileAdapter())//
            .registerTypeAdapter(HashCode.class, new HashCodeAdapter())//
            .setPrettyPrinting()//
            .enableComplexMapKeySerialization()//
            .create();

    public static void sneaky(ThrowingRunnable<Throwable> tr) {
        try {
            tr.run();
        } catch (Throwable t) {
            throwUnchecked(t);
        }
    }

    public static <T> T sneaky(ThrowingProducer<T, Throwable> tp) {
        try {
            return tp.get();
        } catch (Throwable t) {
            throwUnchecked(t);
            return null;//Un possible
        }
    }

    /**
     * Wrapper for {@link Gson#fromJson(Reader, Class)} except from a File.
     *
     * @param file     The file to read from.
     * @param classOfT The class to use.
     * @return The parsed object.
     */
    public static <T> T fromJson(Gson gson, File file, Class<T> classOfT) {
        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, classOfT);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read json from file. " + file);
        }
    }

    /**
     * Wrapper for {@link Gson#fromJson(Reader, Type)} except from a File.
     *
     * @param file    The file to read from.
     * @param typeOfT The type to use.
     * @return The parsed object.
     */
    public static <T> T fromJson(Gson gson, File file, Type typeOfT) {
        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, typeOfT);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read json from file. " + file);
        }
    }

    /**
     * Wrapper for {@link Gson#toJson(Object, Appendable)} except to a File.
     *
     * @param obj  The Object to write.
     * @param file The file to write to.
     */
    public static void toJson(Gson gson, Object obj, File file) {
        toJson(gson, obj, obj.getClass(), file);
    }

    /**
     * Wrapper for {@link Gson#toJson(Object, Type, Appendable)} except to a File.
     *
     * @param obj       The object to write.
     * @param typeOfObj The Type to use.
     * @param file      The File to write to.
     */
    public static void toJson(Gson gson, Object obj, Type typeOfObj, File file) {
        try (FileWriter writer = new FileWriter(makeFile(file))) {
            gson.toJson(obj, typeOfObj, writer);
        } catch (IOException e) {
            throw new RuntimeException("Unable to write json to file. " + file);
        }
    }

    /**
     * Ensures a file exists, creating parent directories if necessary.
     *
     * @param file The file.
     * @return The same file.
     */
    @SuppressWarnings ("ResultOfMethodCallIgnored")
    public static File makeFile(File file) {
        if (!file.exists()) {
            File p = file.getAbsoluteFile().getParentFile();
            if (!p.exists()) {
                p.mkdirs();
            }
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create a new file.", e);
            }
        }
        return file;
    }

    /**
     * Copies a File from one location to another.
     *
     * @param in  From.
     * @param out To.
     */
    public static void copyFile(File in, File out) {
        try (FileInputStream fis = new FileInputStream(in)) {
            try (FileOutputStream fos = new FileOutputStream(makeFile(out))) {
                copy(fis, fos);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy file from: '" + in + "', to: '" + out + "'.", e);
        }
    }

    /**
     * Reads an InputStream to a byte array.
     *
     * @param is The InputStream.
     * @return The bytes.
     * @throws IOException If something is bork.
     */
    public static byte[] toBytes(@WillNotClose InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        copy(is, os);
        return os.toByteArray();
    }

    /**
     * Copies the content of an InputStream to an OutputStream.
     *
     * @param is The InputStream.
     * @param os The OutputStream.
     * @throws IOException If something is bork.
     */
    public static void copy(@WillNotClose InputStream is, @WillNotClose OutputStream os) throws IOException {
        byte[] buffer = bufferCache.get();
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }

    public static void extract(File zip, Path toPath) throws IOException {
        try (StitchUtil.FileSystemDelegate fs = StitchUtil.getJarFileSystem(zip, true)) {
            Path base = fs.get().getPath("/");
            Files.walk(base).forEach(path -> {
                Path to = toPath.resolve(base.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    if (!Files.exists(to)) {
                        sneaky(() -> Files.createDirectory(to));
                    }
                } else {
                    sneaky(() -> Files.createFile(to));
                    sneaky(() -> Files.copy(path, to, StandardCopyOption.REPLACE_EXISTING));
                }
            });
        }
    }

    /**
     * Copies the content of the provided File to the provided Hasher.
     *
     * @param hasher The Hasher.
     * @param file   The File.
     */
    public static void addToHasher(Hasher hasher, File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            addToHasher(hasher, fis);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read file: " + file, e);
        }
    }

    /**
     * Copies the content of the provided InputStream to the provided Hasher.
     *
     * @param hasher The hasher.
     * @param is     The InputStream.
     * @throws IOException If something is bork.
     */
    public static void addToHasher(Hasher hasher, @WillNotClose InputStream is) throws IOException {
        byte[] buffer = bufferCache.get();
        int len;
        while ((len = is.read(buffer)) != -1) {
            hasher.putBytes(buffer, 0, len);
        }
    }

    public static File singleOutput(TaskProvider<? extends Task> provider) {
        return singleOutput(provider.get());
    }

    /**
     * Asserts the task has a single file output and retrieves said file.
     *
     * @param task The task.
     * @return The single file output.
     */
    public static File singleOutput(Task task) {
        return task.getOutputs().getFiles().getSingleFile();
    }

    public static String resolveString(Object obj) {
        if (obj instanceof Closure) {
            obj = ((Closure) obj).call();
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        if (obj instanceof CharSequence) {
            return obj.toString();
        }
        throw new IllegalStateException("Object is not an instance of String or CharSequence.");
    }

    public static URL resolveURL(Object obj) {
        if (obj instanceof Closure) {
            obj = ((Closure) obj).call();
        }
        if (obj instanceof URL) {
            return (URL) obj;
        } else if (obj instanceof CharSequence) {
            try {
                return new URL(obj.toString());
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Malformed URL: " + obj.toString(), e);
            }
        }
        throw new IllegalArgumentException("Object is not an instance of String or CharSequence.");
    }

    public static Closure<File> laterTaskOutput(TaskProvider<? extends Task> task) {
        return laterTaskOutput(task.get());
    }

    public static Closure<File> laterTaskOutput(Task task) {
        return laterFile(() -> singleOutput(task));
    }

    /**
     * Returns a groovy closure for a single file.
     * This is useful for various gradle things where a closure can be provided for file inputs.
     *
     * @param supplier The callback to get the File object.
     * @return The Closure.
     */
    @SuppressWarnings ("unchecked")
    public static Closure<File> laterFile(Supplier<File> supplier) {
        return (Closure<File>) later(supplier);
    }

    @SuppressWarnings ("unchecked")
    public static Closure<String> laterString(Supplier<String> supplier) {
        return (Closure<String>) later(supplier);
    }

    @SuppressWarnings ("unchecked")
    public static Closure<URL> laterURL(Supplier<URL> supplier) {
        return (Closure<URL>) later(supplier);
    }

    public static Closure later(Supplier supplier) {
        return new Closure<Object>(Utils.class) {
            //@formatter:off
            @Override public Object call() { return supplier.get(); }
            @Override public Object call(Object... args) { return call(); }
            @Override public Object call(Object arguments) { return call(); }
            //@formatter:on
        };
    }

    public static String escapeString(String str) {
        if (str.contains(" ")) {
            return StringUtils.wrapIfMissing(str, "\"");
        }
        return str;
    }

    public static String escapeJoin(List<String> things) {
        return things.stream().map(Utils::escapeString).collect(Collectors.joining(" "));
    }

    /**
     * Throws an exception without compiler warnings.
     */
    @SuppressWarnings ("unchecked")
    public static <T extends Throwable> void throwUnchecked(Throwable t) throws T {
        throw (T) t;
    }

    private static class FileAdapter extends TypeAdapter<File> {

        @Override
        public void write(JsonWriter out, File value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.value(value.getAbsolutePath());
        }

        @Override
        public File read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return new File(in.nextString());
        }
    }

    private static class HashCodeAdapter extends TypeAdapter<HashCode> {

        @Override
        public void write(JsonWriter out, HashCode value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.value(value.toString());
        }

        @Override
        public HashCode read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return HashCode.fromString(in.nextString());
        }
    }

    @SuppressWarnings ("unchecked")
    private static class LowerCaseEnumAdapterFactory implements TypeAdapterFactory {

        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (!type.getRawType().isEnum()) {
                return null;
            }
            Map<String, T> lookup = new HashMap<>();
            for (T e : (T[]) type.getRawType().getEnumConstants()) {
                lookup.put(e.toString().toLowerCase(Locale.ROOT), e);
            }
            return new TypeAdapter<T>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                    } else {
                        out.value(value.toString().toLowerCase());
                    }
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        return null;
                    }
                    String name = in.nextString();
                    return name == null ? null : lookup.get(name.toLowerCase(Locale.ROOT));
                }
            };
        }
    }
}

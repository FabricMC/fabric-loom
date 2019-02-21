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

package net.fabricmc.loom.tasks.fernflower;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.struct.ContextUnit;
import org.jetbrains.java.decompiler.struct.IDecompiledData;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructContext;
import org.jetbrains.java.decompiler.struct.lazy.LazyLoader;
import org.jetbrains.java.decompiler.util.DataInputFullStream;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Uses some reflection to replace {@link ContextUnit} instances with a threaded impl.
 * Ensures DecompileContext is setup for each thread.
 * Forces {@link ThreadSafeResultSaver} as the IResultSaver.
 *
 * It is unknown what will happen if multiple input jar's are provided.
 * A single Output jar is forced, please use with caution as many assumptions are made.
 *
 * TODO, Move this to Fabric's fork of FernFlower.
 *
 * Created by covers1624 on 11/02/19.
 */
public class ThreadedFernflower {

    public static final Field f_units;
    public static final Field f_structContext;

    public static final Field f_type;
    public static final Field f_archivePath;
    public static final Field f_filename;
    public static final Field f_resultSaver;
    public static final Field f_decompiledData;
    public static final Field f_classEntries;
    public static final Field f_dirEntries;
    public static final Field f_otherEntries;
    public static final Field f_manifest;

    public static final Field f_properties;
    public static final Field f_logger;
    public static final Field f_structContext2;
    public static final Field f_classProcessor;
    public static final Field f_poolInterceptor;

    static {
        try {
            f_units = setAccessible(StructContext.class.getDeclaredField("units"));
            f_structContext = setAccessible(Fernflower.class.getDeclaredField("structContext"));

            Class<ContextUnit> c_ContextUnit = ContextUnit.class;
            f_type = setAccessible(c_ContextUnit.getDeclaredField("type"));
            f_archivePath = setAccessible(c_ContextUnit.getDeclaredField("archivePath"));
            f_filename = setAccessible(c_ContextUnit.getDeclaredField("filename"));
            f_resultSaver = setAccessible(c_ContextUnit.getDeclaredField("resultSaver"));
            f_decompiledData = setAccessible(c_ContextUnit.getDeclaredField("decompiledData"));
            f_classEntries = setAccessible(c_ContextUnit.getDeclaredField("classEntries"));
            f_dirEntries = setAccessible(c_ContextUnit.getDeclaredField("dirEntries"));
            f_otherEntries = setAccessible(c_ContextUnit.getDeclaredField("otherEntries"));
            f_manifest = setAccessible(c_ContextUnit.getDeclaredField("manifest"));

            Class<DecompilerContext> c_DecompilerContext = DecompilerContext.class;
            f_properties = setAccessible(c_DecompilerContext.getDeclaredField("properties"));
            f_logger = setAccessible(c_DecompilerContext.getDeclaredField("logger"));
            f_structContext2 = setAccessible(c_DecompilerContext.getDeclaredField("structContext"));
            f_classProcessor = setAccessible(c_DecompilerContext.getDeclaredField("classProcessor"));
            f_poolInterceptor = setAccessible(c_DecompilerContext.getDeclaredField("poolInterceptor"));

        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public Fernflower fernFlower;
    public File output;
    public File lineMapFile;
    public int numThreads;
    private DecompilerContext rootContext;

    public ThreadedFernflower(Map<String, Object> options, IFernflowerLogger logger) {
        if (logger == null) {
            logger = new NoopFFLogger();
        }
        fernFlower = new Fernflower(ThreadedFernflower::getBytecode, new ThreadSafeResultSaver(output, lineMapFile), options, logger);
        rootContext = DecompilerContext.getCurrentContext();
    }

    public void addSource(File source) {
        fernFlower.addSource(source);
    }

    public void addLibrary(File library) {
        fernFlower.addLibrary(library);
    }

    public void setOutput(File output) {
        this.output = output;
    }

    public void setLineMapFile(File lineMapFile) {
        this.lineMapFile = lineMapFile;
    }

    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    @SuppressWarnings ("unchecked")
    public void decompileContext() {
        try {
            StructContext context = (StructContext) f_structContext.get(fernFlower);
            Map<String, ContextUnit> units = (Map<String, ContextUnit>) f_units.get(context);
            for (Map.Entry<String, ContextUnit> entry : units.entrySet()) {
                entry.setValue(new ThreadedContextUnit(entry.getValue()));
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        fernFlower.decompileContext();
    }

    public static byte[] getBytecode(String externalPath, String internalPath) throws IOException {
        File file = new File(externalPath);
        if (internalPath == null) {
            return InterpreterUtil.getBytes(file);
        } else {
            try (ZipFile archive = new ZipFile(file)) {
                ZipEntry entry = archive.getEntry(internalPath);
                if (entry == null) {
                    throw new IOException("Entry not found: " + internalPath);
                }
                return InterpreterUtil.getBytes(archive, entry);
            }
        }
    }

    private static <T extends AccessibleObject> T setAccessible(T t) {
        t.setAccessible(true);
        return t;
    }

    @SuppressWarnings ("unchecked")
    public static <T> T get(Field field, Object instance) {
        try {
            return (T) field.get(instance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public class ThreadedContextUnit extends ContextUnit {

        private final int type;
        private final String archivePath;
        private final String filename;
        private final boolean own;
        private final IResultSaver resultSaver;
        private final IDecompiledData decompiledData;

        private final List<String> classEntries = new ArrayList<>();
        private final List<String> dirEntries = new ArrayList<>();
        private final List<String[]> otherEntries = new ArrayList<>();

        private List<StructClass> classes = new ArrayList<>();
        private Manifest manifest;

        public ThreadedContextUnit(ContextUnit from) {
            this(get(f_type, from), get(f_archivePath, from), get(f_filename, from), from.isOwn(), get(f_resultSaver, from), get(f_decompiledData, from));
            classEntries.addAll(get(f_classEntries, from));
            dirEntries.addAll(get(f_dirEntries, from));
            otherEntries.addAll(get(f_otherEntries, from));
            classes.addAll(from.getClasses());
            manifest = get(f_manifest, from);
        }

        public ThreadedContextUnit(int type, String archivePath, String filename, boolean own, IResultSaver resultSaver, IDecompiledData decompiledData) {
            super(type, archivePath, filename, own, resultSaver, decompiledData);
            this.type = type;
            this.archivePath = archivePath;
            this.filename = filename;
            this.own = own;
            this.resultSaver = resultSaver;
            this.decompiledData = decompiledData;
        }

        @Override
        public void reload(LazyLoader loader) throws IOException {
            List<StructClass> lstClasses = new ArrayList<>();

            for (StructClass cl : classes) {
                String oldName = cl.qualifiedName;

                StructClass newCl;
                try (DataInputFullStream in = loader.getClassStream(oldName)) {
                    newCl = new StructClass(in, cl.isOwn(), loader);
                }

                lstClasses.add(newCl);

                LazyLoader.Link lnk = loader.getClassLink(oldName);
                loader.removeClassLink(oldName);
                loader.addClassLink(newCl.qualifiedName, lnk);
            }

            classes = lstClasses;
        }

        @Override
        public void save() {
            switch (type) {
                case TYPE_FOLDER:
                    break;
                case TYPE_JAR:
                case TYPE_ZIP: {
                    resultSaver.saveFolder(archivePath);
                    resultSaver.createArchive(archivePath, filename, manifest);

                    for (String dirEntry : dirEntries) {
                        resultSaver.saveDirEntry(archivePath, filename, dirEntry);
                    }

                    for (String[] pair : otherEntries) {
                        if (type != TYPE_JAR || !JarFile.MANIFEST_NAME.equalsIgnoreCase(pair[1])) {
                            resultSaver.copyEntry(pair[0], archivePath, filename, pair[1]);
                        }
                    }
                    List<Future<?>> futures = new LinkedList<>();
                    ExecutorService executor = Executors.newFixedThreadPool(numThreads);

                    for (int i = 0; i < classes.size(); i++) {
                        StructClass cl = classes.get(i);
                        String entryName = decompiledData.getClassEntryName(cl, classEntries.get(i));
                        if (entryName != null) {
                            futures.add(executor.submit(() -> {
                                setContext();
                                String content = decompiledData.getClassContent(cl);
                                int[] mapping = null;
                                if (DecompilerContext.getOption(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING)) {
                                    mapping = DecompilerContext.getBytecodeSourceMapper().getOriginalLinesMapping();
                                }
                                resultSaver.saveClassEntry(archivePath, filename, cl.qualifiedName, entryName, content, mapping);
                            }));
                        }
                    }
                    executor.shutdown();
                    for (Future<?> future : futures) {
                        try {
                            future.get();
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    resultSaver.closeArchive(archivePath, filename);
                    break;
                }
            }

        }

        public void setContext() {
            DecompilerContext current = DecompilerContext.getCurrentContext();
            if (current == null) {
                current = new DecompilerContext(//
                        get(f_properties, rootContext),//
                        get(f_logger, rootContext),//
                        get(f_structContext2, rootContext),//
                        get(f_classProcessor, rootContext),//
                        get(f_poolInterceptor, rootContext)//
                );
                DecompilerContext.setCurrentContext(current);
            }
        }

        @Override
        public void setManifest(Manifest manifest) {
            super.setManifest(manifest);
            this.manifest = manifest;
        }

        @Override
        public boolean isOwn() {
            return own;
        }

        @Override
        public List<StructClass> getClasses() {
            return classes;
        }
    }
}

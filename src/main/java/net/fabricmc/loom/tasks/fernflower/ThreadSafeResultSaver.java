package net.fabricmc.loom.tasks.fernflower;

import net.fabricmc.loom.util.Utils;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by covers1624 on 18/02/19.
 */
public class ThreadSafeResultSaver implements IResultSaver {

    private final File output;
    private final File lineMapFile;

    public Map<String, ZipOutputStream> outputStreams = new HashMap<>();
    public Map<String, ExecutorService> saveExecutors = new HashMap<>();
    public PrintWriter lineMapWriter;

    public ThreadSafeResultSaver(File output, File lineMapFile) {
        this.output = output;
        this.lineMapFile = lineMapFile;
    }

    @Override
    public void createArchive(String path, String archiveName, Manifest manifest) {
        String key = path + "/" + archiveName;
        File file = Utils.makeFile(output);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            ZipOutputStream zos = manifest == null ? new ZipOutputStream(fos) : new JarOutputStream(fos, manifest);
            outputStreams.put(key, zos);
            saveExecutors.put(key, Executors.newSingleThreadExecutor());
        } catch (IOException e) {
            throw new RuntimeException("Unable to create archive: " + file, e);
        }
        if (lineMapFile != null) {
            try {
                lineMapWriter = new PrintWriter(new FileWriter(Utils.makeFile(lineMapFile)));
            } catch (IOException e) {
                throw new RuntimeException("Unable to create LineMap file.", e);
            }
        }
    }

    @Override
    public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content, int[] mapping) {
        String key = path + "/" + archiveName;
        ExecutorService executor = saveExecutors.get(key);
        executor.submit(() -> {
            ZipOutputStream zos = outputStreams.get(key);
            try {
                zos.putNextEntry(new ZipEntry(entryName));
                if (content != null) {
                    zos.write(content.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                DecompilerContext.getLogger().writeMessage("Cannot write entry " + entryName, e);
            }
            if (lineMapWriter != null) {
                int maxLine = 0;
                int maxLineDist = 0;
                //2 loops here is meh, perhaps merge with a buffer?
                for (int i = 0; i < mapping.length; i += 2) {
                    maxLine = Math.max(maxLine, mapping[i]);
                    maxLineDist = Math.max(maxLineDist, mapping[i + 1]);
                }
                lineMapWriter.println(qualifiedName + "\t" + maxLine + "\t" + maxLineDist);
                for (int i = 0; i < mapping.length; i += 2) {
                    lineMapWriter.println("\t" + mapping[i] + "\t" + mapping[i + 1]);
                }
            }
        });
    }

    @Override
    public void closeArchive(String path, String archiveName) {
        String key = path + "/" + archiveName;
        ExecutorService executor = saveExecutors.get(key);
        Future<?> closeFuture = executor.submit(() -> {
            ZipOutputStream zos = outputStreams.get(key);
            try {
                zos.close();
            } catch (IOException e) {
                throw new RuntimeException("Unable to close zip. " + key, e);
            }
        });
        executor.shutdown();
        try {
            closeFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        outputStreams.remove(key);
        saveExecutors.remove(key);
        if (lineMapWriter != null) {
            lineMapWriter.flush();
            lineMapWriter.close();
        }
    }

    //@formatter:off
    @Override public void saveFolder(String path) { }
    @Override public void copyFile(String source, String path, String entryName) { }
    @Override public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) { }
    @Override public void saveDirEntry(String path, String archiveName, String entryName) { }
    @Override public void copyEntry(String source, String path, String archiveName, String entry) { }
    //@formatter:on
}

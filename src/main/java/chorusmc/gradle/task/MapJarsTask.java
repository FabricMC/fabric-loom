package chorusmc.gradle.task;

import cuchaz.enigma.Deobfuscator;
import cuchaz.enigma.mapping.MappingsEnigmaReader;
import cuchaz.enigma.throwables.MappingParseException;
import groovy.lang.Closure;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecResult;
import org.gradle.process.JavaExecSpec;
import chorusmc.gradle.ChorusGradleExtension;
import chorusmc.gradle.util.Constants;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

public class MapJarsTask extends DefaultTask {

    Deobfuscator deobfuscator;

    @TaskAction
    public void mapJars() throws IOException, MappingParseException {
        ChorusGradleExtension extension = this.getProject().getExtensions().getByType(ChorusGradleExtension.class);
        if(Constants.MINECRAFT_CLIENT_MAPPED_JAR.get(extension).exists()){
            Constants.MINECRAFT_CLIENT_MAPPED_JAR.get(extension).delete();
        }
        this.getLogger().lifecycle(":unpacking mappings");
        if(Constants.MAPPINGS_DIR.exists()){
            FileUtils.deleteDirectory(Constants.MAPPINGS_DIR);
        }
        ZipUtil.unpack(Constants.MAPPINGS_ZIP, Constants.MAPPINGS_DIR);

        this.getLogger().lifecycle(":remapping jar");
        deobfuscator = new Deobfuscator(new JarFile(Constants.MINECRAFT_CLIENT_JAR.get(extension)));
        this.deobfuscator.setMappings(new MappingsEnigmaReader().read(new File(Constants.MAPPINGS_DIR, "pomf-master" + File.separator + "mappings")));
        this.deobfuscator.writeJar(Constants.MINECRAFT_CLIENT_MAPPED_JAR.get(extension), new ProgressListener());

        File tempAssests = new File(Constants.CACHE_FILES, "tempAssets");

        ZipUtil.unpack(Constants.MINECRAFT_CLIENT_JAR.get(extension), tempAssests, name -> {
            if (name.startsWith("assets") || name.startsWith("log4j2.xml")) {
                return name;
            } else {
                return null;
            }
        });
        ZipUtil.unpack(Constants.MINECRAFT_CLIENT_MAPPED_JAR.get(extension), tempAssests);

        ZipUtil.pack(tempAssests, Constants.MINECRAFT_CLIENT_MAPPED_JAR.get(extension));
    }

    public static class ProgressListener implements Deobfuscator.ProgressListener {
        @Override
        public void init(int i, String s) {

        }

        @Override
        public void onProgress(int i, String s) {

        }
    }


}

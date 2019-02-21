package net.fabricmc.loom.tasks.ide;

import com.google.common.io.Files;
import groovy.util.Node;
import groovy.xml.XmlUtil;
import net.fabricmc.loom.util.RunConfiguration;
import net.fabricmc.loom.util.Utils;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.ImmutableMap.of;
import static net.fabricmc.loom.util.Utils.escapeJoin;

/**
 * Generates RunConfigurations for Idea.
 * Specifically for .idea Folder based projects.
 *
 * The Generated RunConfigurations are stored in `.idea/runConfigurations/${config.name}.xml`
 * and are imported as a 'shared' configuration.
 *
 * Created by covers1624 on 20/02/19.
 */
public class GenIdeaRunConfigsTask extends GenRunConfigsTask {

    @TaskAction
    public void doTask() throws IOException {
        File ideaFolder = getProject().file(".idea");
        if (!ideaFolder.exists()) {
            throw new RuntimeException(".idea folder does not exist. You are either running this task manually or your project is not in the .idea Folder format.");
        }
        File runConfigFolder = new File(ideaFolder, "runConfigurations");
        gen(runConfigFolder, getClientRun());
        gen(runConfigFolder, getServerRun());
    }

    private void gen(File folder, RunConfiguration config) throws IOException {
        File configFile = new File(folder, config.getName() + ".xml");
        List<String> vmArgs = new ArrayList<>();
        config.getSysProps().forEach((k, v) -> vmArgs.add("-D" + k + "=" + Utils.escapeString(v)));
        vmArgs.addAll(config.getVmArgs());

        Node node = new Node(null, "component", of("name", "ProjectRunConfigurationManager"));
        Node configNode = node.appendNode("configuration", of("name", config.getName(), "type", "Application", "factoryName", "Application"));
        configNode.appendNode("option", of("name", "MAIN_CLASS_NAME", "value", config.getMainClass()));
        configNode.appendNode("module", of("name", getProject().getName() + "." + config.getSourceSet()));
        configNode.appendNode("option", of("name", "PROGRAM_PARAMETERS", "value", escapeJoin(config.getProgramArgs())));
        configNode.appendNode("option", of("name", "VM_PARAMETERS", "value", String.join(" ", vmArgs)));
        configNode.appendNode("option", of("name", "WORKING_DIRECTORY", "value", config.getWorkingDir().getAbsolutePath()));
        Map<String, String> envVars = config.getEnvVars();
        if (!envVars.isEmpty()) {
            Node envNode = configNode.appendNode("envs");
            envVars.forEach((k, v) -> envNode.appendNode("env", of("name", k, "value", v)));
        }
        Files.asCharSink(Utils.makeFile(configFile), StandardCharsets.UTF_8).write(XmlUtil.serialize(node));
    }
}

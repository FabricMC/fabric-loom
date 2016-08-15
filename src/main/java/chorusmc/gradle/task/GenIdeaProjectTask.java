package chorusmc.gradle.task;

import chorusmc.gradle.ChorusGradleExtension;
import chorusmc.gradle.util.Constants;
import chorusmc.gradle.util.IdeaRunConfig;
import chorusmc.gradle.util.Version;
import com.google.gson.Gson;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class GenIdeaProjectTask extends DefaultTask {
    @TaskAction
    public void genIdeaRuns() throws IOException, ParserConfigurationException, SAXException, TransformerException {
        ChorusGradleExtension extension = this.getProject().getExtensions().getByType(ChorusGradleExtension.class);

        File file = new File(getProject().getName() + ".iml");
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(file);

        Node component = null;
        NodeList module = doc.getElementsByTagName("module").item(0).getChildNodes();
        for (int i = 0; i < module.getLength(); i++) {
            if (module.item(i).getNodeName().equals("component")) {
                component = module.item(i);
                break;
            }
        }

        if (component == null) {
            this.getLogger().lifecycle(":failed to generate intellij run configurations");
            return;
        }

        Node content = null;
        NodeList moduleList = component.getChildNodes();

        for (int i = 0; i < moduleList.getLength(); i++) {
            if (moduleList.item(i).getNodeName().equals("content")) {
                content = moduleList.item(i);
            }
        }

        if (content == null) {
            this.getLogger().lifecycle(":failed to generate intellij run configurations");
            return;
        }

        Element sourceFolder = doc.createElement("sourceFolder");
        sourceFolder.setAttribute("url", "file://$MODULE_DIR$/minecraft/src/main/java");
        sourceFolder.setAttribute("isTestSource", "false");
        content.appendChild(sourceFolder);

        sourceFolder = doc.createElement("sourceFolder");
        sourceFolder.setAttribute("url", "file://$MODULE_DIR$/minecraft/src/main/resources");
        sourceFolder.setAttribute("type", "java-resource");
        content.appendChild(sourceFolder);

        Gson gson = new Gson();

        Version version = gson.fromJson(new FileReader(Constants.MINECRAFT_JSON.get(extension)), Version.class);

        for (Version.Library library : version.libraries) {
            if (library.allowed() && library.getFile() != null && library.getFile().exists()) {
                Element node = doc.createElement("orderEntry");
                node.setAttribute("type", "module-library");
                Element libraryElement = doc.createElement("library");
                Element classes = doc.createElement("CLASSES");
                Element javadoc = doc.createElement("JAVADOC");
                Element sources = doc.createElement("SOURCES");
                Element root = doc.createElement("root");
                root.setAttribute("url", "jar://" + library.getFile().getAbsolutePath() + "!/");
                classes.appendChild(root);
                libraryElement.appendChild(classes);
                libraryElement.appendChild(javadoc);
                libraryElement.appendChild(sources);
                node.appendChild(libraryElement);
                component.appendChild(node);
            } else if (!library.allowed()) {
                this.getLogger().info(":" + library.getFile().getName() + " is not allowed on this os");
            }
        }

        Element node = doc.createElement("orderEntry");
        node.setAttribute("type", "module-library");
        Element libraryElement = doc.createElement("library");
        Element classes = doc.createElement("CLASSES");
        Element javadoc = doc.createElement("JAVADOC");
        Element sources = doc.createElement("SOURCES");
        Element root = doc.createElement("root");
        root.setAttribute("url", "jar://" + Constants.MINECRAFT_CLIENT_MAPPED_JAR.get(extension).getAbsolutePath() + "!/");
        classes.appendChild(root);
        libraryElement.appendChild(classes);
        libraryElement.appendChild(javadoc);
        libraryElement.appendChild(sources);
        node.appendChild(libraryElement);
        component.appendChild(node);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(file);
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(source, result);

        file = new File(getProject().getName() + ".iws");
        docFactory = DocumentBuilderFactory.newInstance();
        docBuilder = docFactory.newDocumentBuilder();
        doc = docBuilder.parse(file);

        NodeList list = doc.getElementsByTagName("component");
        Element runManager = null;
        for (int i = 0; i < list.getLength(); i++) {
            Element element = (Element) list.item(i);
            if (element.getAttribute("name").equals("RunManager")) {
                runManager = element;
                break;
            }
        }

        if (runManager == null) {
            this.getLogger().lifecycle(":failed to generate intellij run configurations");
            return;
        }

        IdeaRunConfig ideaClient = new IdeaRunConfig();
        ideaClient.mainClass = "chorusmc.base.launch.ClientStart";
        ideaClient.projectName = getProject().getName();
        ideaClient.configName = "Minecraft Client";
        ideaClient.runDir = "file://$PROJECT_DIR$/" + extension.runDir;
        ideaClient.arguments = "--assetIndex " + version.assetIndex.id + " --assetsDir " + new File(Constants.CACHE_FILES, "assets").getAbsolutePath();

        runManager.appendChild(ideaClient.genRuns(runManager));

        transformerFactory = TransformerFactory.newInstance();
        transformer = transformerFactory.newTransformer();
        source = new DOMSource(doc);
        result = new StreamResult(file);
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(source, result);

        File runDir = new File(Constants.WORKING_DIRECTORY, extension.runDir);
        if (!runDir.exists()) {
            runDir.mkdirs();
        }
    }
}

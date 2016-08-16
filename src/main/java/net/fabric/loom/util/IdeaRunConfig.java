package net.fabric.loom.util;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.util.Map;

public class IdeaRunConfig {
    public String configName;
    public String projectName;
    public String mainClass;
    public String runDir;
    public String arguments;

    public Element genRuns(Element doc) throws IOException, ParserConfigurationException, TransformerException {
        Element root = this.addXml(doc, "component", ImmutableMap.of("name", "ProjectRunConfigurationManager"));
        root = addXml(root, "configuration", ImmutableMap.of("default", "false", "name", configName, "type", "Application", "factoryName", "Application"));

        this.addXml(root, "module", ImmutableMap.of("name", projectName));
        this.addXml(root, "option", ImmutableMap.of("name", "MAIN_CLASS_NAME", "value", mainClass));
        this.addXml(root, "option", ImmutableMap.of("name", "WORKING_DIRECTORY", "value", runDir));

        if (!Strings.isNullOrEmpty(arguments)) {
            this.addXml(root, "option", ImmutableMap.of("name", "PROGRAM_PARAMETERS", "value", arguments));
        }
        return root;
    }

    public Element addXml(Node parent, String name, Map<String, String> values) {
        Document doc = parent.getOwnerDocument();
        if (doc == null) {
            doc = (Document) parent;
        }

        Element e = doc.createElement(name);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            e.setAttribute(entry.getKey(), entry.getValue());
        }
        parent.appendChild(e);
        return e;
    }
}
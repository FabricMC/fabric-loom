package net.fabricmc.loom.util;

import groovy.lang.Closure;
import org.gradle.util.Configurable;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.*;

/**
 * Created by covers1624 on 21/02/19.
 */
public class RunConfiguration implements Configurable<RunConfiguration> {

    private String name;
    private String mainClass;
    private String sourceSet;
    private File workingDir;
    private List<String> programArgs = new ArrayList<>();
    private List<String> vmArgs = new ArrayList<>();
    private Map<String, String> envVars = new HashMap<>();
    private Map<String, String> sysProps = new HashMap<>();

    public RunConfiguration setName(String name) {
        this.name = name;
        return this;
    }

    public RunConfiguration setMainClass(String mainClass) {
        this.mainClass = mainClass;
        return this;
    }

    public RunConfiguration setSourceSet(String sourceSet) {
        this.sourceSet = sourceSet;
        return this;
    }

    public RunConfiguration setWorkingDir(File workingDir) {
        this.workingDir = workingDir;
        return this;
    }

    //region Program Args
    public RunConfiguration addProgramArg(String arg) {
        programArgs.add(arg);
        return this;
    }

    public RunConfiguration addProgramArgs(List<String> args) {
        programArgs.addAll(args);
        return this;
    }

    public RunConfiguration setProgramArgs(List<String> args) {
        programArgs.clear();
        programArgs.addAll(args);
        return this;
    }
    //endregion

    //region VM Args
    public RunConfiguration addVmArg(String arg) {
        vmArgs.add(arg);
        return this;
    }

    public RunConfiguration addVmArgs(List<String> args) {
        vmArgs.addAll(args);
        return this;
    }

    public RunConfiguration setVmArgs(List<String> args) {
        vmArgs.clear();
        vmArgs.addAll(args);
        return this;
    }
    //endregion

    //region Env Variables
    public RunConfiguration addEnvVar(String key, String value) {
        envVars.put(key, value);
        return this;
    }

    public RunConfiguration addEnvVars(Map<String, String> vars) {
        envVars.putAll(vars);
        return this;
    }

    public RunConfiguration setEnvVars(Map<String, String> vars) {
        envVars.clear();
        envVars.putAll(vars);
        return this;
    }
    //endregion

    //region System Properties
    public RunConfiguration addSysProp(String key, String value) {
        sysProps.put(key, value);
        return this;
    }

    public RunConfiguration addSysProps(Map<String, String> vars) {
        sysProps.putAll(vars);
        return this;
    }

    public RunConfiguration setSysProps(Map<String, String> vars) {
        sysProps.clear();
        sysProps.putAll(vars);
        return this;
    }
    //endregion

    @Override
    public RunConfiguration configure(Closure cl) {
        return ConfigureUtil.configureSelf(cl, this);
    }

    public String getName() {
        return name;
    }

    public String getMainClass() {
        return mainClass;
    }

    public String getSourceSet() {
        return sourceSet;
    }

    public File getWorkingDir() {
        return workingDir;
    }

    public List<String> getProgramArgs() {
        return Collections.unmodifiableList(programArgs);
    }

    public List<String> getVmArgs() {
        return Collections.unmodifiableList(vmArgs);
    }

    public Map<String, String> getEnvVars() {
        return Collections.unmodifiableMap(envVars);
    }

    public Map<String, String> getSysProps() {
        return Collections.unmodifiableMap(sysProps);
    }
}

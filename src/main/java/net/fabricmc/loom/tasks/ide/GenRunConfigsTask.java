package net.fabricmc.loom.tasks.ide;

import net.fabricmc.loom.util.RunConfiguration;
import org.gradle.api.DefaultTask;

/**
 * Created by covers1624 on 21/02/19.
 */
public abstract class GenRunConfigsTask extends DefaultTask {

    private RunConfiguration clientRun;
    private RunConfiguration serverRun;

    //@formatter:off
    public RunConfiguration getClientRun() { return clientRun; }
    public RunConfiguration getServerRun() { return serverRun; }
    public void setClientRun(RunConfiguration clientRun) { this.clientRun = clientRun; }
    public void setServerRun(RunConfiguration serverRun) { this.serverRun = serverRun; }
    //@formatter:on
}

package net.fabricmc.loom.task;

import org.gradle.api.DefaultTask;

public abstract class DefaultLoomTask extends DefaultTask {

	public DefaultLoomTask() {
		setGroup("fabric");
	}

}

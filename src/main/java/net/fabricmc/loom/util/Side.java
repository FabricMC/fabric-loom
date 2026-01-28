package net.fabricmc.loom.util;

public enum Side {
	COMMON(false, false),
	MERGED(true, true),
	CLIENT(true, false),
	SERVER(false, true);

	private final boolean allowClient, allowServer;

	Side(boolean allowClient, boolean allowServer) {
		this.allowClient = allowClient;
		this.allowServer = allowServer;
	}

	public boolean allowClient() {
		return allowClient;
	}

	public boolean allowServer() {
		return allowServer;
	}
}

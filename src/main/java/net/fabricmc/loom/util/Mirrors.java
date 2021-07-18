package net.fabricmc.loom.util;

public class Mirrors {
	public static String LIBRARIES_BASE_MIRRORS;
	public static String RESOURCES_BASE_MIRRORS;
	public static String VERSION_MANIFESTS;

	public static void changeMirror(String lib,String res,String manifest){
		LIBRARIES_BASE_MIRRORS = lib;
		RESOURCES_BASE_MIRRORS = res;
		VERSION_MANIFESTS = manifest;
	}

	public static boolean hasMirror(){
		return ! (LIBRARIES_BASE_MIRRORS.isEmpty()
				 || RESOURCES_BASE_MIRRORS.isEmpty()
				 || VERSION_MANIFESTS.isEmpty());
	}
}

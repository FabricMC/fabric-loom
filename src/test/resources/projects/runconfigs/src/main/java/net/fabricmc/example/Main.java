package net.fabricmc.example;

import net.fabricmc.loader.impl.launch.knot.KnotServer;

public class Main {
	public static void main(String[] args) {
		System.out.println("hello custom main");
		KnotServer.main(args);
	}
}

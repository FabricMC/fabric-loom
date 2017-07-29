package net.fabricmc.loom.util.proccessing;

import java.util.*;

//TODO is this format too verbose? Should something like tiny be used?
public class AccessManipulatorData {

	List<ClassData> data;

	static class ClassData {
		String name;
		List<MemberData> members;
	}

	static class MemberData {
		String name;
		String desc;
		String acc;
	}

	enum Access {
		PUBLIC,
		PROTECTED,
		PRIVATE,
		DEAFULT;


		public static Access fromString(String str){
			return Access.valueOf(str.toUpperCase());
		}

	}
}

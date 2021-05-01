package net.fabricmc.loom.configuration.providers.minecraft.tr;

import org.cadixdev.mercury.Mercury;

public class MercuryUtils {
	public static Mercury copyMercury(Mercury mercury) {
		Mercury copy = new Mercury();
		copy.getClassPath().addAll(mercury.getClassPath());
		copy.getContext().putAll(mercury.getContext());
		copy.getProcessors().addAll(mercury.getProcessors());
		copy.setEncoding(mercury.getEncoding());
		copy.setFlexibleAnonymousClassMemberLookups(mercury.isFlexibleAnonymousClassMemberLookups());
		copy.setGracefulClasspathChecks(mercury.isGracefulClasspathChecks());
		copy.setGracefulJavadocClasspathChecks(mercury.isGracefulJavadocClasspathChecks());
		copy.setSourceCompatibility(mercury.getSourceCompatibility());
		return copy;
	}
}

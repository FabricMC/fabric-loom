plugins {
	id("fabric-loom") version "0.9.local"
	id("maven-publish")
}

val sourceCompatibility = JavaVersion.VERSION_16
val targetCompatibility = JavaVersion.VERSION_16

val archives_base_name : String by project
val mod_version : String by project
val maven_group : String by project

val minecraft_version : String by project
val yarn_mappings : String by project
val loader_version : String by project
val fabric_version : String by project

repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
}

val mixin0 by sourceSets.register("mixin") {
	this.compileClasspath += sourceSets.main.get().compileClasspath
	this.runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

val mixin1 by sourceSets.register("mixin1") {
	this.compileClasspath += sourceSets.main.get().compileClasspath
	this.runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${minecraft_version}")
	mappings("net.fabricmc:yarn:${yarn_mappings}:v2")
	modImplementation("net.fabricmc:fabric-loader:${loader_version}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	modImplementation("net.fabricmc.fabric-api:fabric-api:${fabric_version}")

	// PSA: Some older mods, compiled on Loom 0.2.1, might have outdated Maven POMs.
	// You may need to force-disable transitiveness on them.
}

tasks {
	withType(ProcessResources::class) {
		inputs.property("version", mod_version)
		filesMatching("fabric.mod.json") {
			expand(
				"version" to mod_version
			)
		}
	}

	withType(JavaCompile::class).configureEach {
		// ensure that the encoding is set to UTF-8, no matter what the system default is
		// this fixes some edge cases with special characters not displaying correctly
		// see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
		// If Javadoc is generated, this must be specified in that task too.
		this.options.encoding = "UTF-8"

		// Minecraft 1.17 (21w19a) upwards uses Java 16.
		this.options.release.set(16)
	}

	jar {
		from("LICENSE") {
			rename { "${it}_${archives_base_name}"}
		}
	}

	val mixinJar by registering(Jar::class) {
		archiveClassifier.set("mixin")
		from(mixin0.output)
	}

	val mixin1Jar by registering(Jar::class) {
		archiveClassifier.set("mixin1")
		from(mixin1.output)
	}

	assemble {
		dependsOn(mixinJar)
		dependsOn(mixin1Jar)
	}
}


loom {
	refmapName = "default-refmap0000.json"
}

mixin {
	add(sourceSets.main.get(), "main-refmap0000.json")
	add(mixin0)
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()
}

val remapJar by tasks.existing
val sourcesJar by tasks.existing
val remapSourcesJar by tasks.existing

// configure the maven publication
publishing {
	publications {
		withType(MavenPublication::class) {
			// add all the jars that should be included when publishing to maven
			artifact(remapJar) {
				builtBy(remapJar)
			}
			artifact(sourcesJar) {
				builtBy(remapSourcesJar)
			}
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}

import java.util.Properties
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	kotlin("jvm") version "2.0.21"
	kotlin("plugin.serialization") version "2.0.21"
	id("fabric-loom")
	`maven-publish`
}

java {
	sourceCompatibility = JavaVersion.VERSION_1_8
	targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {
	withType<JavaCompile> {
		options.release.set(8)
	}
	withType<KotlinCompile> {
		compilerOptions {
			jvmTarget = JvmTarget.JVM_1_8
		}
	}
}

group = "com.example"
version = "0.0.1"

dependencies {
	minecraft("com.mojang:minecraft:1.16.5")
	mappings("net.fabricmc:yarn:1.16.5+build.5:v2")
	modImplementation("net.fabricmc:fabric-loader:0.16.9")
	modImplementation("net.fabricmc:fabric-language-kotlin:1.12.3+kotlin.2.0.21")
}

publishing {
	publications {
		create<MavenPublication>("mavenKotlin") {
			from(components["java"])
		}
	}
}

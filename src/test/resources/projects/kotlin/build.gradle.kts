import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

plugins {
	kotlin("jvm") version "1.9.22"
	kotlin("plugin.serialization") version "1.9.22"
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
	withType<KotlinCompile<KotlinJvmOptions>> {
		kotlinOptions {
			jvmTarget = "1.8"
		}
	}
}

group = "com.example"
version = "0.0.1"

dependencies {
	minecraft(group = "com.mojang", name = "minecraft", version = "1.16.5")
	mappings(group = "net.fabricmc", name = "yarn", version = "1.16.5+build.5", classifier = "v2")
	modImplementation("net.fabricmc:fabric-loader:0.12.12")
	modImplementation(group = "net.fabricmc", name = "fabric-language-kotlin", version = "1.10.17+kotlin.1.9.22")
}

publishing {
	publications {
		create<MavenPublication>("mavenKotlin") {
			from(components["java"])
		}
	}
}

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

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
	modImplementation("net.fabricmc:fabric-loader:0.16.9")
	modImplementation(group = "net.fabricmc", name = "fabric-language-kotlin", version = "1.12.3+kotlin.2.0.21")
}

publishing {
	publications {
		create<MavenPublication>("mavenKotlin") {
			from(components["java"])
		}
	}
}

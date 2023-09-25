/**
 * Generates a java source file containing all of the version from the Gradle version catalog.
 */
import java.nio.file.Files
import java.time.LocalDate

generateVersionConstants(sourceSets.main, "runtimeLibs", "net/fabricmc/loom/util/LoomVersions")
generateVersionConstants(sourceSets.test, "testLibs", "net/fabricmc/loom/test/LoomTestVersions")

def generateVersionConstants(def sourceSet, def catalogName, def sourcesName) {
	def versionCatalog = extensions.getByType(VersionCatalogsExtension.class).named(catalogName)

	def task = tasks.register("${catalogName}GenerateConstants", GenerateVersions.class) {
		versionCatalog.getLibraryAliases().forEach {
			def lib = versionCatalog.findLibrary(it).get().get()
			getVersions().put(it, lib.toString())
		}

		className = sourcesName
		headerFile = file("HEADER")
		outputDir = file("src/${sourceSet.name}/generated")
	}

	sourceSet.java.srcDir task
	spotlessGroovyGradle.dependsOn task // Not quite sure why this is needed, but it fixes a warning.
	compileKotlin.dependsOn task
	sourcesJar.dependsOn task
}

abstract class GenerateVersions extends DefaultTask {
	@Input
	abstract MapProperty<String, String> getVersions()

	@Input
	abstract Property<String> getClassName()

	@InputFile
	abstract RegularFileProperty getHeaderFile()

	@OutputDirectory
	abstract DirectoryProperty getOutputDir()

	@TaskAction
	def run() {
		def output = outputDir.get().asFile.toPath()
		output.deleteDir()

		def className = getClassName().get()
		def si = className.lastIndexOf("/")
		def packageName = className.substring(0, si)
		def packagePath = output.resolve(packageName)
		def sourceName = className.substring(si + 1, className.length())
		def sourcePath = packagePath.resolve(sourceName + ".java")
		Files.createDirectories(packagePath)

		def constants = getVersions().get().collect { entry ->
				def split = entry.value.split(":")
				if (split.length != 3) return ""
				"\tpublic static final ${sourceName} ${toSnakeCase(entry.key)} = new ${sourceName}(\"${split[0]}\", \"${split[1]}\", \"${split[2]}\");"
		}.findAll { !it.blank }.join("\n")

		def header = headerFile.get().getAsFile().text.replace("\$YEAR", "${LocalDate.now().year}").trim()

		sourcePath.write(
"""${header}

package ${packageName.replace("/", ".")};

/**
 * Auto generated class, do not edit.
 */
public record ${sourceName}(String group, String module, String version) {
${constants}

	public String mavenNotation() {
		return "%s:%s:%s".formatted(group, module, version);
	}
}
""")
	}

	static def toSnakeCase(String input) {
		return input.trim().replaceAll(/[^a-zA-Z0-9]+/, '_').toUpperCase()
	}
}
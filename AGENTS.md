# Agent Notes

Fabric Loom is the Gradle toolchain used to develop, remap, and build Fabric mods for Minecraft.
Approach this repository as a Gradle expert: be highly fluent in Gradle internals, builds, and debugging, and apply that knowledge carefully while still verifying assumptions in code and task output.

- Do not run the full Gradle build or the entire test suite at once; they take many hours in this repository.
- Prefer targeted Gradle tasks that validate only the area you changed.
- When verifying final changes, run `./gradlew build -x test`.

# Forgified Loom

Talk to us on [Discord](https://discord.gg/C2RdJDpRBP)!

A fork of [Juuxel's Loom fork]("https://github.com/Juuxel/fabric-loom") that is a fork of [Fabric Loom](https://github.com/FabricMC/fabric-loom) that supports the Forge modding toolchain.

A [Gradle](https://gradle.org/) plugin to setup a deobfuscated development environment for Minecraft mods. Primarily used in the Fabric toolchain.

* Has built in support for tiny mappings (Used by [Yarn](https://github.com/FabricMC/yarn))
* Utilises the Fernflower and CFR decompilers to generate source code with comments.
* Designed to support modern versions of Minecraft (Tested with 1.14.4 and upwards)
* ~~Built in support for IntelliJ IDEA, Eclipse and Visual Studio Code to generate run configurations for Minecraft.~~
* Loom targets a wide range of Gradle versions. _Tested with 4.9 up to 6.7_
* Supports the latest version of Java all the way down to Java 8

## Usage

Starting with a Fabric project similar to the example mod,

## Use Loom to develop mods

To get started developing your own mods please follow the guide on [Setting up a mod development environment](https://fabricmc.net/wiki/tutorial:setup).

## Debugging Loom (Only needed if you want to work on Loom itself)

_This guide assumes you are using IntelliJ IDEA, other IDE's have not been tested; your experience may vary._

Then you need to set `loom.forge = true` in your `gradle.properties`,
and add the Forge dependency:

```groovy
forge "net.minecraftforge:forge:1.16.4-35.1.7"
```

You also need to remove the Fabric Loader and Fabric API dependencies.
You should also remove any access wideners and replace them with a Forge AT.

### Mixins

Mixins are used with a property in the `loom` block in build.gradle:

```groovy
loom {
	mixinConfig = "mymod.mixins.json"
}
```

## Limitations

- Launching via IDE run configs doesn't work on Eclipse or VSCode.
- The srg -> yarn remapper used for coremod class names is *really* simple,
  and might break with coremods that have multiple class names per line.

## Known Issues
- https://github.com/architectury/forgified-fabric-loom/issues
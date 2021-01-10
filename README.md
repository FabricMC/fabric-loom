# Forgified Loom

Talk to us on [Discord](https://discord.gg/C2RdJDpRBP)!

A fork of [Juuxel's Loom fork]("https://github.com/Juuxel/fabric-loom") that is a fork of [Fabric Loom](https://github.com/FabricMC/fabric-loom) that supports the Forge modding toolchain.

Note that if ForgeGradle works fine for you, *use it*.
This is not meant to be a complete replacement for ForgeGradle,
and there are probably many bugs and limitations here that FG doesn't have.

## Usage

Starting with a Fabric project similar to the example mod,

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
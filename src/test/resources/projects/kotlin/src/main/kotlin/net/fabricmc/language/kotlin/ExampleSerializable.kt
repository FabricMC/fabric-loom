package net.fabricmc.language.kotlin

import kotlinx.serialization.Serializable

import net.minecraft.util.Identifier

@Serializable
data class ExampleSerializable(@Serializable(with = IdentifierSerializer::class) val identifier: Identifier, val test: Double) {
}
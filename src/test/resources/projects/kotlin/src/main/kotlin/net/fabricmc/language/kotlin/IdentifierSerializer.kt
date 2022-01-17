package net.fabricmc.language.kotlin

import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor

import net.minecraft.util.Identifier

// Based on https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-core/kotlinx-serialization-core/kotlinx.serialization.descriptors/-primitive-serial-descriptor.html
class IdentifierSerializer : KSerializer<Identifier> {
    override val descriptor =
        PrimitiveSerialDescriptor("net.fabricmc.language.kotlin.IdentifierSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Identifier {
        val split = decoder.decodeString().split(':')
        return Identifier(split[0], split[1])
    }

    override fun serialize(encoder: Encoder, value: Identifier) {
        encoder.encodeString("${value.namespace}:${value.path}")
    }
}
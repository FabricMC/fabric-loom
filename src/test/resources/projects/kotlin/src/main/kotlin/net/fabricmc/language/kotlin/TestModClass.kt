package net.fabricmc.language.kotlin

import net.fabricmc.api.ModInitializer
import org.apache.logging.log4j.LogManager
import kotlinx.serialization.*
import kotlinx.serialization.json.*

import net.minecraft.util.Identifier

class TestModClass : ModInitializer {
    val logger = LogManager.getFormatterLogger("KotlinLanguageTest")

    override fun onInitialize() {
        val ident = Identifier("kotlin:hello")
        val json = Json.encodeToString(ExampleSerializable(ident, 12.0))
        val obj = Json.decodeFromString<ExampleSerializable>(json)

        logger.info("**************************")
        logger.info("Hello from Kotlin TestModClass")
        logger.info(json)
        logger.info(obj)
        logger.info(ident.testExt())
        logger.info("**************************")
    }
}
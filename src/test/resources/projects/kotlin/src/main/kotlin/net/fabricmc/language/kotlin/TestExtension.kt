package net.fabricmc.language.kotlin

import net.minecraft.entity.Entity
import net.minecraft.util.Identifier

class TestExtension {
    fun testExtCompile() {
        val entity: Entity? = null
        entity!!.testExt()
    }
}

fun Entity.testExt() {
    velocityDirty = true
}

fun Identifier.testExt(): String {
    return "Hello ext"
}
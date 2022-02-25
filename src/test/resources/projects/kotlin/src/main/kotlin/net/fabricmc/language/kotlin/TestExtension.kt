package net.fabricmc.language.kotlin

import net.minecraft.entity.Entity

class TestExtension {
    fun testExtCompile() {
        val entity: Entity? = null
        entity!!.testExt()
    }
}

fun Entity.testExt() {
    velocityDirty = true
}
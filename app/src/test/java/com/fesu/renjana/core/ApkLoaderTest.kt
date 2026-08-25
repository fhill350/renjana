package com.fesu.renjana.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ApkLoaderTest {
    @Test
    fun relativeActivityNameUsesPackagePrefix() {
        assertEquals(
            "com.example.MainActivity",
            ApkLoader.qualifyActivityName("com.example", ".MainActivity")
        )
    }

    @Test
    fun shortActivityNameUsesPackagePrefix() {
        assertEquals(
            "com.example.MainActivity",
            ApkLoader.qualifyActivityName("com.example", "MainActivity")
        )
    }

    @Test
    fun fullyQualifiedActivityNameIsUnchanged() {
        assertEquals(
            "com.other.MainActivity",
            ApkLoader.qualifyActivityName("com.example", "com.other.MainActivity")
        )
    }

    @Test
    fun shortActivityNameWithoutPackageStaysShort() {
        assertEquals(
            "MainActivity",
            ApkLoader.qualifyActivityName(null, "MainActivity")
        )
    }
}

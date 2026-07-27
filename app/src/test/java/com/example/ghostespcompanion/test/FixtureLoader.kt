package com.example.ghostespcompanion.test

import java.io.IOException

object FixtureLoader {
    fun load(name: String): String {
        val path = "fixtures/$name"
        val cls = FixtureLoader::class.java
        val resource = cls.classLoader!!.getResource(path)
            ?: cls.getResource("/$path")
            ?: throw IOException("Fixture '$name' not found on classpath")
        return resource.readText(Charsets.UTF_8)
    }

    fun loadLines(name: String): List<String> = load(name).lines().filter { it.isNotEmpty() }
}

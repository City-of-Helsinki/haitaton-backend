package fi.hel.haitaton.hanke

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue

fun <T> String.asJsonResource(type: Class<T>): T =
    OBJECT_MAPPER.readValue(getResourceAsText(this), type)

inline fun <reified T : Any> String.asJsonResource(): T =
    OBJECT_MAPPER.readValue(getResourceAsText(this))

fun String.asJsonNode(): JsonNode = OBJECT_MAPPER.readTree(getResourceAsText(this))

fun getResourceAsText(path: String): String =
    // The class here is arbitrary, could be any class.
    // Using ClassLoader might be cleaner, but it would require changing every resource file
    // path anywhere in the test files.
    JsonNode::class.java.getResource(path)!!.readText(Charsets.UTF_8)

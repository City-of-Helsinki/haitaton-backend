package fi.hel.haitaton.hanke

import com.fasterxml.jackson.databind.JsonNode

fun <T> String.asJsonResource(type: Class<T>): T =
    OBJECT_MAPPER.readValue(getResourceAsText(this), type)

fun String.asJsonNode(): JsonNode = OBJECT_MAPPER.readTree(getResourceAsText(this))

fun getResourceAsText(path: String): String =
    // The class here is arbitrary, could be any class.
    // Using ClassLoader might be cleaner, but it would require changing every resource file
    // path anywhere in the test files.
    JsonNode::class.java.getResource(path)!!.readText(Charsets.UTF_8)

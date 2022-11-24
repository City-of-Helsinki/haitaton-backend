package fi.hel.haitaton.hanke

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType

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

/**
 * Find all audit logs for a specific object type. Getting all and filtering would obviously not be
 * acceptable in production, but in tests we usually have a very limited number of entities at any
 * one test.
 *
 * This way we don't have to add a new repository method only used in tests.
 */
fun AuditLogRepository.findByType(type: ObjectType) =
    this.findAll().filter { it.message.auditEvent.target.type == type }

fun AuditLogRepository.countByType(type: ObjectType) =
    this.findAll().count { it.message.auditEvent.target.type == type }

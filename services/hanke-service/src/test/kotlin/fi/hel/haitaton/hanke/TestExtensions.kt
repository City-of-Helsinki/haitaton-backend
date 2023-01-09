package fi.hel.haitaton.hanke

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.springframework.test.web.servlet.ResultActions

fun <T> String.asJsonResource(type: Class<T>): T =
    OBJECT_MAPPER.readValue(this.getResourceAsText(), type)

inline fun <reified T : Any> String.asJsonResource(): T =
    OBJECT_MAPPER.readValue(this.getResourceAsText())

/** Read the response body from a MockMvc result and deserialize from JSON. */
inline fun <reified T> ResultActions.andReturnBody(): T =
    OBJECT_MAPPER.readValue(andReturn().response.getContentAsString(StandardCharsets.UTF_8))

fun String.getResourceAsText(): String =
    // The class here is arbitrary, could be any class.
    // Using ClassLoader might be cleaner, but it would require changing every resource file
    // path anywhere in the test files.
    JsonNode::class.java.getResource(this)!!.readText(Charsets.UTF_8)

/**
 * Find all audit logs for a specific object type. Getting all and filtering would obviously not be
 * acceptable in production, but in tests we usually have a very limited number of entities at any
 * one test.
 *
 * This way we don't have to add a new repository method only used in tests.
 */
fun AuditLogRepository.findByType(type: ObjectType) =
    this.findAll().filter { it.message.auditEvent.target.type == type }

fun OffsetDateTime.asUtc(): OffsetDateTime = this.withOffsetSameInstant(ZoneOffset.UTC)

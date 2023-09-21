package fi.hel.haitaton.hanke

import assertk.Assert
import assertk.assertions.containsExactly
import com.fasterxml.jackson.module.kotlin.readValue
import com.icegreen.greenmail.junit5.GreenMailExtension
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType
import jakarta.mail.internet.MimeMessage
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions
import org.springframework.test.web.servlet.ResultActions

fun <T> String.asJsonResource(type: Class<T>): T =
    OBJECT_MAPPER.readValue(this.getResourceAsText(), type)

inline fun <reified T : Any> String.asJsonResource(): T =
    OBJECT_MAPPER.readValue(this.getResourceAsText())

/** Read the response body from a MockMvc result and deserialize from JSON. */
inline fun <reified T> ResultActions.andReturnBody(): T =
    OBJECT_MAPPER.readValue(andReturn().response.getContentAsString(StandardCharsets.UTF_8))

fun String.getResourceAsBytes(): ByteArray = this.getResource().readBytes()

inline fun <reified T> String.parseJson(): T = OBJECT_MAPPER.readValue(this)

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

fun GreenMailExtension.firstReceivedMessage(): MimeMessage {
    Assertions.assertEquals(1, receivedMessages.size)
    return receivedMessages[0]
}

inline fun <reified T> Assert<List<T>>.hasSameElementsAs(elements: List<T>) =
    containsExactly(*elements.toTypedArray<T>())

/**
 * "Uses" a variable without doing anything with it. Used to avoid "Parameter is never used"
 * warnings when compiling. Especially useful for ParameterizedTests if there are parameters meant
 * to be used in the name of the test.
 */
fun Any?.touch() = Unit

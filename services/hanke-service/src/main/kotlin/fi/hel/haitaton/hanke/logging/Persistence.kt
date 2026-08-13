package fi.hel.haitaton.hanke.logging

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import org.hibernate.annotations.Generated
import org.hibernate.annotations.Type
import org.hibernate.generator.EventType
import org.springframework.data.jpa.repository.JpaRepository
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize

/**
 * Version for the schema of the audit log message schema. Change when making changes to the
 * [AuditLogEvent] object tree. The changes need to be also done to the Elasticsearch schema, and
 * they should be somehow synchronized.
 */
const val AUDIT_LOG_SCHEMA_VERSION = "1"

/**
 * This needs to match
 * https://helsinkisolutionoffice.atlassian.net/wiki/spaces/HELFI/pages/8033697816/Logging+Transferring+log+entries+to+elastic+using+reusable+component?NO_SSR=1#Schema
 *
 * Deliberately a plain class, not a `data class`: a data class's generated `equals`/`hashCode`
 * would cover [id], which is null until Hibernate assigns it post-insert, so an instance's hashCode
 * would change across its own lifecycle - breaking it in any hash-based collection. Its generated
 * `copy()` would also invite the mistake of "updating" a managed entity by producing an unmanaged
 * one Hibernate doesn't know about, instead of mutating the tracked instance in place. Equality is
 * by [id] alone, matching this codebase's other entities with generated ids (e.g.
 * [fi.hel.haitaton.hanke.attachment.common.AttachmentEntity]). The `val` properties are still
 * populated by Hibernate via reflection through the no-arg constructor kotlin("plugin.jpa")
 * generates for `@Entity` classes - same as [fi.hel.haitaton.hanke.allu.AlluEventEntity].
 */
@Entity
@Table(name = "audit_logs")
class AuditLogEntryEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,

    /**
     * This will always be false in hanke-service. The log transfer component will set this to true
     * after the logs are sent successfully.
     */
    @Column(name = "is_sent") val isSent: Boolean = false,

    /** The message in JSON as a jsonb column. */
    @Column(columnDefinition = "json") @Type(JsonBinaryType::class) val message: AuditLogMessage,

    /** This will be set by the database. */
    @Column(name = "created_at")
    @Generated(event = [EventType.INSERT])
    val createdAt: OffsetDateTime? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuditLogEntryEntity

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "AuditLogEntryEntity(id=$id, isSent=$isSent, message=$message, createdAt=$createdAt)"
}

data class AuditLogMessage(@JsonProperty("audit_event") val auditEvent: AuditLogEvent)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuditLogEvent(
    @JsonProperty("date_time")
    @JsonSerialize(using = CustomOffsetDateTimeSerializer::class)
    @JsonDeserialize(using = CustomOffsetDateTimeDeserializer::class)
    val dateTime: OffsetDateTime,
    val operation: Operation,
    val status: Status,
    @JsonProperty("failure_description") val failureDescription: String?,
    @JsonProperty("app_version") val appVersion: String = AUDIT_LOG_SCHEMA_VERSION,
    val actor: AuditLogActor,
    val target: AuditLogTarget,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuditLogActor(
    @JsonProperty("user_id") val userId: String?,
    val role: UserRole,
    @JsonProperty("ip_address") val ipAddress: String?,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuditLogTarget(
    val id: String,
    val type: ObjectType,
    @JsonProperty("object_before") val objectBefore: String?,
    @JsonProperty("object_after") val objectAfter: String?,
)

interface AuditLogRepository : JpaRepository<AuditLogEntryEntity, Long> {
    // No need for additional functions. Only adding entries from Haitaton app.
}

/**
 * A custom serializer to make sure the date_time field is in the right format (ISO 8601). We can't
 * directly specify which [tools.jackson.databind.ObjectMapper] Hibernate uses when serializing the
 * message as JSON - it's the same shared instance built by
 * [fi.hel.haitaton.hanke.configuration.GeoJsonAwareObjectMapperSupplier] that every JSON column
 * uses. We could reconfigure that mapper's date/time handling to force this format everywhere, but
 * that might have implications elsewhere, which could lead to really hard bugs. Specifying custom
 * serializers and deserializers is not the prettiest solution, but still cleaner than changing
 * project-wide configurations.
 *
 * Based on https://www.baeldung.com/jackson-serialize-dates#java-8-no-dependency
 */
class CustomOffsetDateTimeSerializer : ValueSerializer<OffsetDateTime>() {

    override fun serialize(value: OffsetDateTime, gen: JsonGenerator, ctxt: SerializationContext) {
        gen.writeString(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }
}

/**
 * See [CustomOffsetDateTimeSerializer] for rationale.
 *
 * Based on: https://www.baeldung.com/jackson-serialize-dates#java-8-no-dependency
 */
class CustomOffsetDateTimeDeserializer : ValueDeserializer<OffsetDateTime>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): OffsetDateTime =
        OffsetDateTime.parse(p.string, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

package fi.hel.haitaton.hanke.logging

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.GenerationTime
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Version for the schema of the audit log message schema. Change when making changes to the
 * [AuditLogEvent] object tree. The changes need to be also done to the Elasticsearch schema, and
 * they should be somehow synchronized.
 */
const val AUDIT_LOG_SCHEMA_VERSION = "1"

/**
 * This needs to match
 * https://helsinkisolutionoffice.atlassian.net/wiki/spaces/HELFI/pages/8033697816/Logging+Transferring+log+entries+to+elastic+using+reusable+component?NO_SSR=1#Schema
 */
@Entity
@Table(name = "audit_logs")
@TypeDef(name = "json", typeClass = JsonBinaryType::class)
data class AuditLogEntryEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,

    /**
     * This will always be false in hanke-service. The log transfer component will set this to true
     * after the logs are sent successfully.
     */
    @Column(name = "is_sent") val isSent: Boolean = false,

    /** The message in JSON as a jsonb column. */
    @Column(columnDefinition = "json") @Type(type = "json") val message: AuditLogMessage,

    /** This will be set by the database. */
    @Column(name = "created_at")
    @Generated(GenerationTime.INSERT)
    val createdAt: LocalDateTime? = null,
)

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
    val id: String?,
    val type: ObjectType,
    @JsonProperty("object_before") val objectBefore: String?,
    @JsonProperty("object_after") val objectAfter: String?,
)

interface AuditLogRepository : JpaRepository<AuditLogEntryEntity, UUID> {
    // No need for additional functions. Only adding entries from Haitaton app.
}

/**
 * A custom serializer to make sure the date_time field is in the right format (ISO 8601). We can't
 * directly specify which [com.fasterxml.jackson.databind.ObjectMapper] Hibernate uses when
 * serializing the message as JSON, so we can't just tell it to use
 * [com.fasterxml.jackson.datatype.jsr310.JavaTimeModule]. We could add configuration that forces
 * the object mapper everywhere, but that might have implications elsewhere, which could lead to
 * really hard bugs. Specifying custom serializers and deserializers is not the prettiest solution,
 * but still cleaner than changing project-wide configurations.
 *
 * Based on https://www.baeldung.com/jackson-serialize-dates#java-8-no-dependency
 */
class CustomOffsetDateTimeSerializer @JvmOverloads constructor(t: Class<OffsetDateTime?>? = null) :
    StdSerializer<OffsetDateTime?>(t) {

    override fun serialize(
        value: OffsetDateTime?,
        gen: JsonGenerator,
        arg2: SerializerProvider?,
    ) {
        gen.writeString(value?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }
}

/**
 * See [CustomOffsetDateTimeSerializer] for rationale.
 *
 * Based on: https://www.baeldung.com/jackson-serialize-dates#java-8-no-dependency
 */
class CustomOffsetDateTimeDeserializer
@JvmOverloads
constructor(t: Class<OffsetDateTime?>? = null) : StdDeserializer<OffsetDateTime?>(t) {
    override fun deserialize(
        jsonparser: JsonParser,
        context: DeserializationContext?
    ): OffsetDateTime? =
        OffsetDateTime.parse(jsonparser.text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

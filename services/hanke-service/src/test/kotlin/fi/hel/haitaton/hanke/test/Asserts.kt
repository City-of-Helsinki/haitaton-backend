package fi.hel.haitaton.hanke.test

import assertk.Assert
import assertk.all
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.extracting
import assertk.assertions.hasClass
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.TextNode
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.hakemus.Hakemusalue
import fi.hel.haitaton.hanke.hakemus.PostalAddress
import fi.hel.haitaton.hanke.hakemus.StreetAddress
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.zonedDateTime
import jakarta.mail.internet.MimeMessage
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAmount
import java.util.UUID
import org.geojson.Polygon

object Asserts {

    fun Assert<Hanke>.hasSameGeometryAs(hakemusalueet: List<Hakemusalue>) {
        given { hanke ->
            val hankeCoordinates =
                hanke.alueet
                    .flatMap { it.geometriat!!.featureCollection!!.features }
                    .map { it.geometry as Polygon }
                    .map { it.coordinates }
            assertThat(hankeCoordinates)
                .hasSameElementsAs(
                    hakemusalueet.map { it.geometries().flatMap { g -> g.coordinates } }
                )
        }
    }

    fun Assert<OffsetDateTime?>.isRecent(offset: TemporalAmount = Duration.ofMinutes(1)) {
        val now = OffsetDateTime.now()
        isNotNull().isBetween(now.minus(offset), now)
    }

    fun Assert<Instant?>.isRecentInstant(offset: TemporalAmount = Duration.ofMinutes(1)) {
        val now = Instant.now()
        isNotNull().isBetween(now.minus(offset), now)
    }

    fun Assert<Instant?>.isGreaterThan(other: Instant?) {
        given { instant ->
            assertThat(instant).isNotNull()
            assertThat(other).isNotNull()
            assertThat(instant!! > other!!).isTrue()
        }
    }

    fun Assert<ZonedDateTime?>.isRecentZDT(offset: TemporalAmount = Duration.ofMinutes(1)) {
        val now = ZonedDateTime.now()
        isNotNull().isBetween(now.minus(offset), now)
    }

    fun Assert<LocalDateTime?>.isRecentUTC(offset: TemporalAmount = Duration.ofMinutes(1)) =
        isNotNull().prop(LocalDateTime::zonedDateTime).isRecentZDT(offset)

    fun Assert<OffsetDateTime>.isSameInstantAs(expected: OffsetDateTime) {
        this.prop(OffsetDateTime::toInstant).isEqualTo(expected.toInstant())
    }

    fun Assert<Array<MimeMessage>>.hasReceivers(vararg receivers: String?) {
        extracting { it.allRecipients.first().toString() }.containsExactlyInAnyOrder(*receivers)
    }

    /**
     * Blob location has a format of 123/ab7993b7-a775-4eac-b5b7-8546332944fe. Hanke or application
     * id followed by a slash and a UUID.
     */
    fun Assert<String>.isValidBlobLocation(id: Number) = given { actual ->
        assertThat(actual).contains("/")
        val idPart = actual.substringBefore("/")
        val uuidPart = actual.substringAfter("/")
        assertThat(idPart.toLongOrNull()).isEqualTo(id.toLong())
        assertThat(UUID.fromString(uuidPart)).isNotNull()
    }

    fun Assert<PostalAddress?>.hasStreetName(street: String) =
        isNotNull()
            .prop(PostalAddress::streetAddress)
            .prop(StreetAddress::streetName)
            .isEqualTo(street)

    fun Assert<ValidationResult>.failedWith(vararg paths: String) = all {
        this.prop(ValidationResult::isOk).isFalse()
        this.prop(ValidationResult::errorPaths).containsExactlyInAnyOrder(*paths)
    }

    fun Assert<ValidationResult>.isSuccess() = this.prop(ValidationResult::isOk).isTrue()

    fun Assert<JsonNode>.hasNullNode(path: String) = hasPath(path).hasClass(NullNode::class)

    fun Assert<JsonNode>.hasTextNode(path: String) =
        hasPath(path).isInstanceOf(TextNode::class).transform { node: JsonNode -> node.textValue() }

    private fun Assert<JsonNode>.hasPath(path: String) =
        transform { node: JsonNode -> node.get(path) }.isNotNull()
}

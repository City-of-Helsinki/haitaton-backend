package fi.hel.haitaton.hanke.test

import assertk.Assert
import assertk.all
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.extracting
import assertk.assertions.first
import assertk.assertions.hasSize
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.domain.HasFeatures
import fi.hel.haitaton.hanke.zonedDateTime
import jakarta.mail.internet.MimeMessage
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAmount
import java.util.UUID
import org.geojson.Feature
import org.geojson.FeatureCollection
import org.geojson.Geometry

object Asserts {

    fun Assert<OffsetDateTime?>.isRecent(offset: TemporalAmount = Duration.ofMinutes(1)) =
        given { actual ->
            if (actual == null) return
            val now = OffsetDateTime.now()
            assertThat(actual).isBetween(now.minus(offset), now)
        }

    fun Assert<ZonedDateTime?>.isRecentZDT(offset: TemporalAmount = Duration.ofMinutes(1)) =
        given { actual ->
            if (actual == null) return
            val now = ZonedDateTime.now()
            assertThat(actual).isBetween(now.minus(offset), now)
        }

    fun Assert<LocalDateTime>.isRecentUTC(offset: TemporalAmount = Duration.ofMinutes(1)) =
        prop(LocalDateTime::zonedDateTime).isRecentZDT(offset)

    fun Assert<OffsetDateTime>.isSameInstantAs(expected: OffsetDateTime) {
        this.prop(OffsetDateTime::toInstant).isEqualTo(expected.toInstant())
    }

    fun <T> Assert<Feature>.hasSameCoordinatesAs(other: Geometry<T>) {
        prop(Feature::getGeometry)
            .isInstanceOf(Geometry::class)
            .prop("coordinates") { it.coordinates }
            .isEqualTo(other.coordinates)
    }

    fun <T> Assert<Hankealue>.hasSingleGeometryWithCoordinates(other: Geometry<T>) {
        prop(Hankealue::geometriat)
            .isNotNull()
            .prop(HasFeatures::featureCollection)
            .isNotNull()
            .all {
                prop(FeatureCollection::getFeatures).hasSize(1)
                prop(FeatureCollection::getFeatures).first().hasSameCoordinatesAs(other)
            }
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
        assertThat(idPart.toIntOrNull()).isEqualTo(id)
        assertThat(UUID.fromString(uuidPart)).isNotNull()
    }
}

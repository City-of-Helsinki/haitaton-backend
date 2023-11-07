package fi.hel.haitaton.hanke.test

import assertk.Assert
import assertk.all
import assertk.assertions.first
import assertk.assertions.hasSize
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.domain.HasFeatures
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAmount
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
}

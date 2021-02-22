package fi.hel.haitaton.hanke

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

const val SRID = 3879

const val COORDINATE_SYSTEM_URN = "urn:ogc:def:crs:EPSG::$SRID"

val OBJECT_MAPPER = jacksonObjectMapper().apply {
    this.registerModule(JavaTimeModule())
    this.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}

val TZ_UTC: ZoneId = ZoneId.of("UTC")

val DATABASE_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

// Note: database definition has no limit, so this is sort of important; must be quite long, but not excessive (considering database size etc.)
const val MAXIMUM_TYOMAAKATUOSOITE_LENGTH = 2000
val MAXIMUM_DATE: ZonedDateTime = ZonedDateTime.of(2099, 12, 31, 23, 59, 59, 999999999, TZ_UTC)

const val HANKETUNNUS_PREFIX = "HAI"

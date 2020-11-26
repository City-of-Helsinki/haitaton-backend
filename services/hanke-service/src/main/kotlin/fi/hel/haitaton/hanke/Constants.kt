package fi.hel.haitaton.hanke

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val SRID = 3879

const val COORDINATE_SYSTEM_URN = "urn:ogc:def:crs:EPSG::$SRID"

val OBJECT_MAPPER = jacksonObjectMapper().apply {
    this.registerModule(JavaTimeModule())
    this.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}

val TZ_UTC: ZoneId = ZoneId.of("UTC")

val DATABASE_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

package fi.hel.haitaton.hanke

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.ZoneId

const val COORDINATE_SYSTEM_URN = "urn:ogc:def:crs:EPSG::3879"

val OBJECT_MAPPER = jacksonObjectMapper().apply { this.registerModule(JavaTimeModule()) }

val TZ_UTC: ZoneId = ZoneId.of("UTC")

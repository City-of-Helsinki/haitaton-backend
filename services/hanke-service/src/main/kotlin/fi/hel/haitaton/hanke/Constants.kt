package fi.hel.haitaton.hanke

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

const val COORDINATE_SYSTEM = "EPSG:3879"
const val COORDINATE_SYSTEM_URN = "urn:ogc:def:crs:EPSG::3879"

val objectMapper = jacksonObjectMapper()

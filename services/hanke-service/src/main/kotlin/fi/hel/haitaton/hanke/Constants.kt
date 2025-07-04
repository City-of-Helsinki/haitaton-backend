package fi.hel.haitaton.hanke

import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

const val SRID = 3879

const val COORDINATE_SYSTEM_URN = "urn:ogc:def:crs:EPSG::$SRID"

val OBJECT_MAPPER = createObjectMapper()

val TZ_UTC: ZoneOffset = ZoneOffset.UTC

val DATABASE_TIMESTAMP_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

/** Hanke names are limited to 100 characters in the DB. */
const val MAXIMUM_HANKE_NIMI_LENGTH = 100

/** Hanke alue names are limited to 100 characters in the DB. */
const val MAXIMUM_HANKE_ALUE_NIMI_LENGTH = 100

// Note: database definition has no limit, so this is sort of important; must be quite long,
// but not excessive (considering database size etc.)
const val MAXIMUM_TYOMAAKATUOSOITE_LENGTH = 2000
val MAXIMUM_DATE: ZonedDateTime = ZonedDateTime.of(2099, 12, 31, 23, 59, 59, 999999999, TZ_UTC)

const val HANKETUNNUS_PREFIX = "HAI"

const val ALLOWED_ATTACHMENT_COUNT = 20

const val HANKEALUE_DEFAULT_NAME = "Hankealue"

const val MONTHS_BEFORE_DELETION: Long = 6

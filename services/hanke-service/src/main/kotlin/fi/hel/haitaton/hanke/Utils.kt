package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationContactType.ASIANHOITAJA
import fi.hel.haitaton.hanke.application.ApplicationContactType.HAKIJA
import fi.hel.haitaton.hanke.application.ApplicationContactType.RAKENNUTTAJA
import fi.hel.haitaton.hanke.application.ApplicationContactType.TYON_SUORITTAJA
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.domain.BusinessId
import fi.hel.haitaton.hanke.permissions.ApplicationUserContact
import fi.hel.haitaton.hanke.permissions.HankeKayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankeUserContact
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import mu.KotlinLogging
import org.springframework.security.core.context.SecurityContextHolder

private val logger = KotlinLogging.logger {}

private val businessIdRegex = "^(\\d{7})-(\\d)\$".toRegex()
private val businessIdMultipliers = listOf(7, 9, 10, 5, 8, 4, 2)

/**
 * Returns the current time in UTC, with time zone info.
 *
 * Truncated to microseconds, since that's the database precision.
 */
fun getCurrentTimeUTC(): ZonedDateTime = ZonedDateTime.now(TZ_UTC).truncatedTo(ChronoUnit.MICROS)

/**
 * Returns the current time in UTC, without time zone info (i.e. LocalTime instance).
 *
 * Truncated to microseconds, since that's the database precision.
 */
fun getCurrentTimeUTCAsLocalTime(): LocalDateTime = getCurrentTimeUTC().toLocalDateTime()

fun currentUserId(): String = SecurityContextHolder.getContext().authentication.name

/**
 * Valid businessId (y-tunnus) requirements:
 * 1. format NNNNNNN-T, where N = sequence number and T = check number.
 * 2. documentation of check mark calculation:
 * ```
 *     - https://www.vero.fi/globalassets/tietoa-verohallinnosta/ohjelmistokehittajille/yritys--ja-yhteis%C3%B6tunnuksen-ja-henkil%C3%B6tunnuksen-tarkistusmerkin-tarkistuslaskenta.pdf
 * ```
 *
 * Only verifies that the id is of valid form. It does not guarantee that it actually exists.
 */
fun BusinessId.isValidBusinessId(): Boolean {
    logger.info { "Verifying businessId: $this" }

    val matchResult = businessIdRegex.find(this)
    if (matchResult == null) {
        logger.warn { "Invalid format." }
        return false
    }

    val (sequenceNumber, checkDigit) = matchResult.destructured

    val calculatedCheck =
        sequenceNumber
            .map { it.digitToInt() }
            .zip(businessIdMultipliers)
            .sumOf { (num, multiplier) -> num * multiplier }
            .mod(11)
            .let { remainder ->
                when (remainder) {
                    1 -> {
                        logger.warn { "Remainder not valid." }
                        return false
                    }
                    0 -> 0
                    else -> 11 - remainder
                }
            }

    return if (calculatedCheck == checkDigit.toInt()) {
        true
    } else {
        logger.warn { "Check digit doesn't match." }
        false
    }
}

fun List<CustomerWithContacts>.ordererCount() = flatMap { it.contacts }.count { it.orderer }

fun userContact(name: String?, email: String?): HankeUserContact? {
    return when {
        name.isNullOrBlank() || email.isNullOrBlank() -> null
        else -> HankeUserContact(name, email)
    }
}

fun userContact(
    name: String?,
    email: String?,
    type: ApplicationContactType
): ApplicationUserContact? =
    when {
        name.isNullOrBlank() || email.isNullOrBlank() -> null
        else -> ApplicationUserContact(name, email, type)
    }

fun ApplicationData.typedContacts(): Set<ApplicationUserContact> =
    when (this) {
        is CableReportApplicationData ->
            listOfNotNull(
                    customerWithContacts.typedContacts(HAKIJA),
                    contractorWithContacts.typedContacts(TYON_SUORITTAJA),
                    representativeWithContacts?.typedContacts(ASIANHOITAJA),
                    propertyDeveloperWithContacts?.typedContacts(RAKENNUTTAJA)
                )
                .flatten()
                .toSet()
    }

fun Set<ApplicationUserContact>.removeInviter(inviter: HankeKayttajaEntity?) =
    if (inviter == null) this else filter { it.email != inviter.sahkoposti }

private fun CustomerWithContacts.typedContacts(
    type: ApplicationContactType
): List<ApplicationUserContact> = contacts.mapNotNull { userContact(it.fullName(), it.email, type) }

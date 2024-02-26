package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import java.time.ZonedDateTime
import java.util.UUID

sealed interface HakemusUpdateRequest {
    val nimi: String
    val katuosoite: String
    val alkuPvm: ZonedDateTime?
    val loppuPvm: ZonedDateTime?
    val tyoalueet: List<ApplicationArea>?
    val yhteystiedot: Map<ApplicationContactType, HakemusyhteystietoRequest>?

    /**
     * Returns true if this application update request has changes compared to the given
     * [applicationEntity].
     */
    fun hasChanges(applicationEntity: ApplicationEntity): Boolean
}

data class JohtoselvityshakemusUpdateRequest(
    // 1. sivu Perustiedot (first filled in Create)
    override val nimi: String,
    override val katuosoite: String,
    val rakennustyo: Boolean,
    val huoltotyo: Boolean,
    val kiinteistoliittyma: Boolean,
    val hatatyo: Boolean,
    val louhintaa: Boolean,
    val tyonKuvaus: String,
    // 2. sivu Alueet
    override val alkuPvm: ZonedDateTime? = null,
    override val loppuPvm: ZonedDateTime? = null,
    override val tyoalueet: List<ApplicationArea>? = null,
    // 3. sivu Yhteystiedot
    override val yhteystiedot: Map<ApplicationContactType, HakemusyhteystietoRequest>? = null,
    // 4. sivu Liitteet (separete endpoint)
    // 5. sivu Yhteenveto (no input data)
) : HakemusUpdateRequest {

    override fun hasChanges(applicationEntity: ApplicationEntity): Boolean {
        val applicationData = applicationEntity.applicationData as CableReportApplicationData
        return nimi != applicationData.name ||
            katuosoite != applicationData.postalAddress?.streetAddress?.streetName ||
            rakennustyo != applicationData.constructionWork ||
            huoltotyo != applicationData.maintenanceWork ||
            kiinteistoliittyma != applicationData.propertyConnectivity ||
            hatatyo != applicationData.emergencyWork ||
            louhintaa != applicationData.rockExcavation ||
            tyonKuvaus != applicationData.workDescription ||
            alkuPvm != applicationData.startTime ||
            loppuPvm != applicationData.endTime ||
            tyoalueet != applicationData.areas ||
            yhteystiedot.hasChanges(applicationEntity.yhteystiedot)
    }
}

/**
 * For updating an existing [Hakemusyhteystieto] (with [id]) or creating a new one (without [id]).
 */
data class HakemusyhteystietoRequest(
    /** Hakemusyhteystieto id */
    val id: UUID? = null,
    val tyyppi: CustomerType? = null,
    val nimi: String? = null,
    val sahkoposti: String? = null,
    val puhelin: String? = null,
    val ytunnus: String? = null,
    val yhteyshenkilot: List<HakemusyhteyshenkiloRequest>? = null,
) {

    /**
     * Returns true if this customer has changes compared to the given [hakemusyhteystietoEntity].
     */
    fun hasChanges(hakemusyhteystietoEntity: HakemusyhteystietoEntity): Boolean =
        tyyppi != hakemusyhteystietoEntity.tyyppi ||
            nimi != hakemusyhteystietoEntity.nimi ||
            sahkoposti != hakemusyhteystietoEntity.sahkoposti ||
            puhelin != hakemusyhteystietoEntity.puhelinnumero ||
            ytunnus != hakemusyhteystietoEntity.ytunnus ||
            yhteyshenkilot.hasChanges(hakemusyhteystietoEntity.yhteyshenkilot)
}

data class HakemusyhteyshenkiloRequest(
    /** Hankekayttaja id */
    val id: UUID,
    val tilaaja: Boolean
)

fun Map<ApplicationContactType, HakemusyhteystietoRequest>?.hasChanges(
    hakemusyhteystiedot: Map<ApplicationContactType, HakemusyhteystietoEntity>
): Boolean {
    if (this == null) {
        return hakemusyhteystiedot.isNotEmpty()
    }
    if (this.size != hakemusyhteystiedot.size) return true
    return this.entries.any { (role, yhteystietoRequest) ->
        hakemusyhteystiedot[role]?.let { entity -> yhteystietoRequest.hasChanges(entity) } ?: true
    }
}

fun List<HakemusyhteyshenkiloRequest>?.hasChanges(
    hakemusyhteyshenkilot: List<HakemusyhteyshenkiloEntity>
): Boolean {
    if (this.isNullOrEmpty()) {
        return hakemusyhteyshenkilot.isNotEmpty()
    }
    if (this.size != hakemusyhteyshenkilot.size) return true
    hakemusyhteyshenkilot
        .map { it.hankekayttaja.id }
        .toSet()
        .let { existingIds ->
            return this.any { hakemusyhteyshenkiloRequest ->
                hakemusyhteyshenkiloRequest.id !in existingIds
            }
        }
}

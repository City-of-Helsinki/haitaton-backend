package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.application.PostalAddress
import java.time.ZonedDateTime
import java.util.UUID

data class Hakemus(
    val id: Long?,
    var alluid: Int?,
    var alluStatus: ApplicationStatus?,
    var applicationIdentifier: String?,
    val applicationType: ApplicationType,
    val applicationData: HakemusData,
    val hankeTunnus: String
)

sealed interface HakemusData {
    var name: String?
    var postalAddress: PostalAddress?
    var startTime: ZonedDateTime?
    var endTime: ZonedDateTime?
    var areas: List<ApplicationArea>?
    var customerWithContacts: Hakemusyhteystieto?

    fun toApplicationData(pendingOnClient: Boolean): ApplicationData

    fun allContactIds(): Set<UUID>
}

data class JohtoselvityshakemusData(
    // 1. sivu Perustiedot
    /** Työn nimi */
    override var name: String? = null,
    /** Katuosoite */
    override var postalAddress: PostalAddress? = null,
    /** Työssä on kyse: Uuden rakenteen tai johdon rakentamisesta */
    var constructionWork: Boolean? = null,
    /** Työssä on kyse: Olemassaolevan rakenteen kunnossapitotyöstä */
    var maintenanceWork: Boolean? = null,
    /** Työssä on kyse: Kiinteistöliittymien rakentamisesta */
    var propertyConnectivity: Boolean? = null,
    /**
     * Työssä on kyse: Kaivutyö on aloitettu ennen johtoselvityksen tilaamista merkittävien
     * vahinkojen välttämiseksi
     */
    var emergencyWork: Boolean? = null,
    /** Louhitaanko työn yhteydessä, esimerkiksi kallioperää? */
    var rockExcavation: Boolean? = null,
    /** Työn kuvaus */
    var workDescription: String? = null,
    // 2. sivu Alueet
    /** Työn arvioitu alkupäivä */
    override var startTime: ZonedDateTime? = null,
    /** Työn arvioitu loppupäivä */
    override var endTime: ZonedDateTime? = null,
    /** Työalueet */
    override var areas: List<ApplicationArea>? = null,
    // 3. sivu Yhteystiedot
    /** Hakijan tiedot */
    override var customerWithContacts: Hakemusyhteystieto? = null,
    /** Työn suorittajan tiedot */
    var contractorWithContacts: Hakemusyhteystieto? = null,
    /** Rakennuttajan tiedot */
    var propertyDeveloperWithContacts: Hakemusyhteystieto? = null,
    /** Asianhoitajan tiedot */
    var representativeWithContacts: Hakemusyhteystieto? = null,
    // 4. sivu Liitteet (separete endpoint)
    // 5. sivu Yhteenveto (no input data)
) : HakemusData {
    /**
     * Creates an [ApplicationData] instance from this [JohtoselvityshakemusData] instance but
     * without any contact information. Contacts are stored in separate [HakemusyhteystietoEntity]
     * entities and are not part of the application data stored in database.
     */
    override fun toApplicationData(pendingOnClient: Boolean): ApplicationData =
        CableReportApplicationData(
            applicationType = ApplicationType.CABLE_REPORT,
            name = name ?: "",
            postalAddress = postalAddress,
            constructionWork = constructionWork ?: false,
            maintenanceWork = maintenanceWork ?: false,
            propertyConnectivity = propertyConnectivity ?: false,
            emergencyWork = emergencyWork ?: false,
            rockExcavation = rockExcavation ?: false,
            workDescription = workDescription ?: "",
            startTime = startTime,
            endTime = endTime,
            areas = areas,
            customerWithContacts =
                CustomerWithContacts(
                    Customer(
                        type = null,
                        name = "",
                        country = "",
                        email = null,
                        phone = null,
                        registryKey = null,
                        ovt = null,
                        invoicingOperator = null,
                        sapCustomerNumber = null
                    ),
                    emptyList()
                ),
            contractorWithContacts =
                CustomerWithContacts(
                    Customer(
                        type = null,
                        name = "",
                        country = "",
                        email = null,
                        phone = null,
                        registryKey = null,
                        ovt = null,
                        invoicingOperator = null,
                        sapCustomerNumber = null
                    ),
                    emptyList()
                ),
            pendingOnClient = pendingOnClient,
        )

    override fun allContactIds(): Set<UUID> {
        val ids = mutableSetOf<UUID>()
        customerWithContacts?.let {
            ids.addAll(it.yhteyshenkilot.map { yhteyshenkilo -> yhteyshenkilo.hankekayttajaId })
        }
        contractorWithContacts?.let {
            ids.addAll(it.yhteyshenkilot.map { yhteyshenkilo -> yhteyshenkilo.hankekayttajaId })
        }
        propertyDeveloperWithContacts?.let {
            ids.addAll(it.yhteyshenkilot.map { yhteyshenkilo -> yhteyshenkilo.hankekayttajaId })
        }
        representativeWithContacts?.let {
            ids.addAll(it.yhteyshenkilot.map { yhteyshenkilo -> yhteyshenkilo.hankekayttajaId })
        }
        return ids
    }
}

fun HakemusyhteystietoEntity?.toHakemusyhteystieto() =
    this?.let {
        Hakemusyhteystieto(
            id = it.id,
            tyyppi = it.tyyppi,
            rooli = it.rooli,
            nimi = it.nimi,
            sahkoposti = it.sahkoposti,
            puhelinnumero = it.puhelinnumero,
            ytunnus = it.ytunnus,
            yhteyshenkilot =
                it.yhteyshenkilot.map { yhteyshenkilo ->
                    Hakemusyhteyshenkilo(
                        hankekayttajaId = yhteyshenkilo.hankekayttaja.id,
                        etunimi = yhteyshenkilo.hankekayttaja.etunimi,
                        sukunimi = yhteyshenkilo.hankekayttaja.sukunimi,
                        sahkoposti = yhteyshenkilo.hankekayttaja.sahkoposti,
                        puhelin = yhteyshenkilo.hankekayttaja.puhelin,
                        tilaaja = yhteyshenkilo.tilaaja,
                    )
                }
        )
    }

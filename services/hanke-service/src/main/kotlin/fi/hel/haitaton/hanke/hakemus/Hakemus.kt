package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.PostalAddress
import java.time.ZonedDateTime

data class Hakemus(
    val id: Long?,
    val alluid: Int?,
    val alluStatus: ApplicationStatus?,
    val applicationIdentifier: String?,
    val applicationType: ApplicationType,
    val applicationData: HakemusData,
    val hankeTunnus: String
)

sealed interface HakemusData {
    val name: String
    val postalAddress: PostalAddress?
    val startTime: ZonedDateTime?
    val endTime: ZonedDateTime?
    val areas: List<ApplicationArea>?
    val customerWithContacts: Hakemusyhteystieto?
}

data class JohtoselvityshakemusData(
    override val name: String,
    override val postalAddress: PostalAddress? = null,
    val constructionWork: Boolean? = null,
    val maintenanceWork: Boolean? = null,
    val propertyConnectivity: Boolean? = null,
    val emergencyWork: Boolean? = null,
    val rockExcavation: Boolean? = null,
    val workDescription: String? = null,
    override val startTime: ZonedDateTime? = null,
    override val endTime: ZonedDateTime? = null,
    override val areas: List<ApplicationArea>? = null,
    override val customerWithContacts: Hakemusyhteystieto? = null,
    val contractorWithContacts: Hakemusyhteystieto? = null,
    val propertyDeveloperWithContacts: Hakemusyhteystieto? = null,
    val representativeWithContacts: Hakemusyhteystieto? = null,
) : HakemusData

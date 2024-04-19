package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import fi.hel.haitaton.hanke.application.ApplicationType

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "applicationType",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(value = CreateJohtoselvityshakemusRequest::class, name = "CABLE_REPORT"),
    JsonSubTypes.Type(value = CreateKaivuilmoitusRequest::class, name = "EXCAVATION_NOTIFICATION")
)
@JsonInclude(JsonInclude.Include.NON_NULL)
sealed interface CreateHakemusRequest {
    val applicationType: ApplicationType
    val name: String
    val workDescription: String
    val hankeTunnus: String
}

data class CreateJohtoselvityshakemusRequest(
    override val applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
    /** Työn nimi */
    override val name: String,
    /** Katuosoite */
    val postalAddress: PostalAddressRequest?,
    /** Työssä on kyse: Uuden rakenteen tai johdon rakentamisesta */
    val constructionWork: Boolean,
    /** Työssä on kyse: Olemassaolevan rakenteen kunnossapitotyöstä */
    val maintenanceWork: Boolean,
    /** Työssä on kyse: Kiinteistöliittymien rakentamisesta */
    val propertyConnectivity: Boolean,
    /**
     * Työssä on kyse: Kaivutyö on aloitettu ennen johtoselvityksen tilaamista merkittävien
     * vahinkojen välttämiseksi
     */
    val emergencyWork: Boolean,
    /** Louhitaanko työn yhteydessä, esimerkiksi kallioperää? */
    val rockExcavation: Boolean,
    /** Työn kuvaus */
    override val workDescription: String,
    override val hankeTunnus: String,
) : CreateHakemusRequest

data class CreateKaivuilmoitusRequest(
    override val applicationType: ApplicationType = ApplicationType.EXCAVATION_NOTIFICATION,
    /** Työn nimi */
    override val name: String,
    /** Työn kuvaus */
    override val workDescription: String,
    /** Työssä on kyse: Uuden rakenteen tai johdon rakentamisesta */
    val constructionWork: Boolean,
    /** Työssä on kyse: Olemassaolevan rakenteen kunnossapitotyöstä */
    val maintenanceWork: Boolean,
    /**
     * Työssä on kyse: Kaivutyö on aloitettu ennen johtoselvityksen tilaamista merkittävien
     * vahinkojen välttämiseksi
     */
    val emergencyWork: Boolean,
    /** Hae uusi johtoselvitys? false = kyllä, true = ei */
    val cableReportDone: Boolean,
    /**
     * Uusi johtoselvitys - Louhitaanko työn yhteydessä, esimerkiksi kallioperää? Täytyy olla
     * annettu, jos [cableReportDone] == false
     */
    val rockExcavation: Boolean? = null,
    /** Tehtyjen johtoselvitysten tunnukset */
    val cableReports: List<String>? = emptyList(),
    /** Sijoitussopimukset */
    val placementContracts: List<String>? = emptyList(),
    /** Työhön vaadittava pätevyys */
    val requiredCompetence: Boolean = false,
    override val hankeTunnus: String,
) : CreateHakemusRequest

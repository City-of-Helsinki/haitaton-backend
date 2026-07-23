package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.hakemus.CreateJohtoselvityshakemusRequest
import fi.hel.haitaton.hanke.hakemus.CreateKaivuilmoitusRequest
import fi.hel.haitaton.hanke.hakemus.PostalAddressRequest
import fi.hel.haitaton.hanke.hakemus.StreetAddress

object CreateHakemusRequestFactory {

    fun johtoselvitysRequest(
        name: String = ApplicationFactory.DEFAULT_APPLICATION_NAME,
        postalAddress: String? = "Kotikatu 1",
        constructionWork: Boolean = false,
        maintenanceWork: Boolean = false,
        propertyConnectivity: Boolean = false,
        emergencyWork: Boolean = false,
        rockExcavation: Boolean = false,
        workDescription: String = ApplicationFactory.DEFAULT_WORK_DESCRIPTION,
        hankeTunnus: String = HankeFactory.DEFAULT_HANKETUNNUS,
    ): CreateJohtoselvityshakemusRequest =
        CreateJohtoselvityshakemusRequest(
            name = name,
            postalAddress = postalAddress?.let { PostalAddressRequest(StreetAddress(it)) },
            constructionWork = constructionWork,
            maintenanceWork = maintenanceWork,
            propertyConnectivity = propertyConnectivity,
            emergencyWork = emergencyWork,
            rockExcavation = rockExcavation,
            workDescription = workDescription,
            hankeTunnus = hankeTunnus,
        )

    fun kaivuilmoitusRequest(
        name: String = ApplicationFactory.DEFAULT_APPLICATION_NAME,
        workDescription: String = ApplicationFactory.DEFAULT_WORK_DESCRIPTION,
        constructionWork: Boolean = false,
        maintenanceWork: Boolean = false,
        emergencyWork: Boolean = false,
        cableReportDone: Boolean = false,
        rockExcavation: Boolean? = false,
        cableReports: List<String>? = emptyList(),
        placementContracts: List<String>? = emptyList(),
        requiredCompetence: Boolean = false,
        hankeTunnus: String = HankeFactory.DEFAULT_HANKETUNNUS,
    ): CreateKaivuilmoitusRequest =
        CreateKaivuilmoitusRequest(
            name = name,
            workDescription = workDescription,
            constructionWork = constructionWork,
            maintenanceWork = maintenanceWork,
            emergencyWork = emergencyWork,
            cableReportDone = cableReportDone,
            cableReports = cableReports,
            placementContracts = placementContracts,
            requiredCompetence = requiredCompetence,
            rockExcavation = rockExcavation,
            hankeTunnus = hankeTunnus,
        )
}

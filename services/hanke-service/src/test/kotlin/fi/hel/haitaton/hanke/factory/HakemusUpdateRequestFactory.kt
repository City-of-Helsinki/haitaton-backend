package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.application.StreetAddress
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.PostalAdressRequest

object HakemusUpdateRequestFactory {

    fun createBlankJohtoselvityshakemusUpdateRequest(): JohtoselvityshakemusUpdateRequest {
        return JohtoselvityshakemusUpdateRequest(
            name = "",
            postalAddress = PostalAdressRequest(StreetAddress("")),
            constructionWork = false,
            maintenanceWork = false,
            propertyConnectivity = false,
            emergencyWork = false,
            rockExcavation = false,
            workDescription = "",
            startTime = null,
            endTime = null,
            areas = null,
            customerWithContacts = null,
            contractorWithContacts = null,
            propertyDeveloperWithContacts = null,
            representativeWithContacts = null
        )
    }
}

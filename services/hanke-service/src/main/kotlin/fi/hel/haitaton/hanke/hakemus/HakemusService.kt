package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HakemusService(
    private val applicationRepository: ApplicationRepository,
) {
    @Transactional(readOnly = true)
    fun hakemusResponse(applicationId: Long): HakemusResponse {
        val applicationEntity =
            applicationRepository.findOneById(applicationId)
                ?: throw ApplicationNotFoundException(applicationId)
        return hakemusResponseWithYhteystiedot(applicationEntity)
    }

    private fun hakemusResponseWithYhteystiedot(applicationEntity: ApplicationEntity) =
        HakemusResponse(
            applicationEntity.id!!,
            applicationEntity.alluid,
            applicationEntity.alluStatus,
            applicationEntity.applicationIdentifier,
            applicationEntity.applicationType,
            hakemusDataResponseWithYhteystiedot(
                applicationEntity.id,
                applicationEntity.applicationData,
                applicationEntity.yhteystiedot
            ),
            applicationEntity.hanke.hankeTunnus
        )

    private fun hakemusDataResponseWithYhteystiedot(
        applicationId: Long,
        applicationData: ApplicationData,
        hakemusyhteystiedot: Map<ApplicationContactType, HakemusyhteystietoEntity>
    ) =
        when (applicationData) {
            is CableReportApplicationData ->
                JohtoselvitysHakemusDataResponse(
                    applicationData.applicationType,
                    applicationData.name,
                    customerWithContactsResponseWithYhteystiedot(
                        hakemusyhteystiedot[ApplicationContactType.HAKIJA]
                    )
                        ?: throw HakemusyhteystietoNotFoundException(
                            applicationId,
                            ApplicationContactType.HAKIJA
                        ),
                    applicationData.areas,
                    applicationData.startTime,
                    applicationData.endTime,
                    applicationData.pendingOnClient,
                    applicationData.workDescription,
                    customerWithContactsResponseWithYhteystiedot(
                        hakemusyhteystiedot[ApplicationContactType.TYON_SUORITTAJA]
                    )
                        ?: throw HakemusyhteystietoNotFoundException(
                            applicationId,
                            ApplicationContactType.TYON_SUORITTAJA
                        ),
                    applicationData.rockExcavation,
                    applicationData.postalAddress,
                    customerWithContactsResponseWithYhteystiedot(
                        hakemusyhteystiedot[ApplicationContactType.ASIANHOITAJA]
                    ),
                    applicationData.invoicingCustomer,
                    applicationData.customerReference,
                    applicationData.area,
                    customerWithContactsResponseWithYhteystiedot(
                        hakemusyhteystiedot[ApplicationContactType.RAKENNUTTAJA]
                    ),
                    applicationData.constructionWork,
                    applicationData.maintenanceWork,
                    applicationData.emergencyWork,
                    applicationData.propertyConnectivity
                )
        }

    private fun customerWithContactsResponseWithYhteystiedot(
        hakemusyhteystieto: HakemusyhteystietoEntity?
    ): CustomerWithContactsResponse? =
        hakemusyhteystieto?.let {
            val customer = it.toCustomerResponse()
            val contacts =
                it.yhteyshenkilot.map { yhteyshenkilo -> yhteyshenkilo.toContactResponse() }
            CustomerWithContactsResponse(customer, contacts)
        }
}

class HakemusyhteystietoNotFoundException(
    applicationId: Long,
    contactType: ApplicationContactType
) :
    RuntimeException(
        "Hakemusyhteystieto not found for application, id = $applicationId, contactType = $contactType"
    )
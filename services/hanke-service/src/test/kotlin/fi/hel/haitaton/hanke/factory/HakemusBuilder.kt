package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloRepository
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoRepository
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService

data class HakemusBuilder(
    private var applicationEntity: ApplicationEntity,
    private val userId: String,
    private val hakemusFactory: HakemusFactory,
    private val hankeKayttajaService: HankeKayttajaService,
    private val applicationRepository: ApplicationRepository,
    private val hankeRepository: HankeRepository,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
) {
    fun save(): ApplicationEntity = applicationRepository.save(applicationEntity)

    fun saveWithYhteystiedot(f: HakemusyhteystietoBuilder.() -> Unit): ApplicationEntity {
        val hakemus = save()
        val entity = applicationRepository.getReferenceById(hakemus.id!!)
        val builder =
            HakemusyhteystietoBuilder(
                entity,
                userId,
                hankeKayttajaFactory,
                hakemusyhteystietoRepository,
                hakemusyhteyshenkiloRepository,
            )
        builder.f()
        return entity
    }

    fun withStatus(
        status: ApplicationStatus = ApplicationStatus.PENDING,
        alluId: Int = 1,
        identifier: String = "JS000$alluId"
    ): HakemusBuilder {
        applicationEntity =
            applicationEntity.copy(
                alluid = alluId,
                alluStatus = status,
                applicationIdentifier = identifier
            )
        return this
    }

    fun withWorkDescription(workDescription: String) = apply {
        applicationEntity.applicationData =
            when (applicationEntity.applicationData) {
                is CableReportApplicationData ->
                    (applicationEntity.applicationData as CableReportApplicationData).copy(
                        workDescription = workDescription
                    )
            }
    }

    fun inHandling(alluId: Int = 1) = withStatus(ApplicationStatus.HANDLING, alluId)
}

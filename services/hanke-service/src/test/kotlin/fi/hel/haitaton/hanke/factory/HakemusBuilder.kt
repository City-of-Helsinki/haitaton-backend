package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloRepository
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoRepository

data class HakemusBuilder(
    private var hakemus: Hakemus,
    private val userId: String,
    private val hakemusService: HakemusService,
    private val applicationRepository: ApplicationRepository,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
) {
    /**
     * Create this application and then update it to give it fuller information. This method does an
     * actual update, so it will set modifiedBy and modifiedAt columns and bump version up to 1.
     */
    fun save(): Hakemus = hakemusService.createHakemus(hakemus, userId)

    fun withHakemusModification(f: Hakemus.() -> Unit): HakemusBuilder = this.apply { hakemus.f() }

    /** Save the entity with [save], and - for convenience - get the saved entity from DB. */
    private fun saveEntity(): ApplicationEntity =
        applicationRepository.getReferenceById(save().id!!)

    fun saveWithYhteystiedot(f: HakemusyhteystietoBuilder.() -> Unit): ApplicationEntity {
        val entity = saveEntity()
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
        hakemus =
            hakemus.copy(alluid = alluId, alluStatus = status, applicationIdentifier = identifier)
        return this
    }

    fun inHandling(alluId: Int = 1) = withStatus(ApplicationStatus.HANDLING, alluId)
}

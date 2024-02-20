package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createApplication
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloRepository
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoRepository
import fi.hel.haitaton.hanke.profiili.ProfiiliClient
import org.springframework.stereotype.Component

@Component
class HakemusFactory(
    private val applicationService: ApplicationService,
    private val profiiliClient: ProfiiliClient,
    private val applicationRepository: ApplicationRepository,
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
) {
    fun builder(userId: String, hankeEntity: HankeEntity): ApplicationBuilder {
        val application = createApplication(hankeTunnus = hankeEntity.hankeTunnus)
        return ApplicationBuilder(
            application,
            userId,
            ProfiiliFactory.DEFAULT_NAMES,
            applicationService,
            applicationRepository,
            profiiliClient,
            hankeKayttajaFactory,
            hakemusyhteystietoRepository,
            hakemusyhteyshenkiloRepository,
        )
    }
}

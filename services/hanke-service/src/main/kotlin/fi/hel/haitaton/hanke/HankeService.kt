package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.CableReportWithoutHanke
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeWithApplications
import org.springframework.transaction.annotation.Transactional

interface HankeService {

    fun loadHanke(hankeTunnus: String): Hanke?

    fun findHankeOrThrow(hankeTunnus: String): Hanke

    fun getHankeId(hankeTunnus: String): Int?

    fun getHankeIdOrThrow(hankeTunnus: String): Int

    fun getHankeWithApplications(hankeTunnus: String): HankeWithApplications

    @Transactional fun createHanke(hanke: Hanke): Hanke

    @Transactional
    fun generateHankeWithApplication(
        cableReport: CableReportWithoutHanke,
        userId: String
    ): HankeWithApplications

    @Transactional fun updateHanke(hanke: Hanke): Hanke

    @Transactional fun deleteHanke(hanke: Hanke, hakemukset: List<Application>, userId: String)

    fun loadPublicHanke(): List<Hanke>

    fun loadHankkeetByIds(ids: List<Int>): List<Hanke>
}

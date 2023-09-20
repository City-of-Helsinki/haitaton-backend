package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.CableReportWithoutHanke
import fi.hel.haitaton.hanke.domain.Hanke
import org.springframework.transaction.annotation.Transactional

interface HankeService {

    fun loadHanke(hankeTunnus: String): Hanke?

    fun findIdentifier(hankeTunnus: String): HankeIdentifier?

    fun getHankeApplications(hankeTunnus: String): List<Application>

    @Transactional fun createHanke(hanke: Hanke): Hanke

    @Transactional
    fun generateHankeWithApplication(
        cableReport: CableReportWithoutHanke,
        userId: String
    ): Application

    @Transactional fun updateHanke(hanke: Hanke): Hanke

    @Transactional fun deleteHanke(hankeTunnus: String, userId: String)

    fun loadPublicHanke(): List<Hanke>

    fun loadHankkeetByIds(ids: List<Int>): List<Hanke>
}

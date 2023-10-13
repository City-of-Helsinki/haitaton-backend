package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.CableReportWithoutHanke
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Hanke

interface HankeService {

    fun loadHanke(hankeTunnus: String): Hanke?

    fun findIdentifier(hankeTunnus: String): HankeIdentifier?

    fun getHankeApplications(hankeTunnus: String): List<Application>

    fun createHanke(request: CreateHankeRequest): Hanke

    fun generateHankeWithApplication(
        cableReport: CableReportWithoutHanke,
        userId: String
    ): Application

    fun updateHanke(hanke: Hanke): Hanke

    fun deleteHanke(hankeTunnus: String, userId: String)

    fun loadPublicHanke(): List<Hanke>

    fun loadHankeById(id: Int): Hanke?

    fun loadHankkeetByIds(ids: List<Int>): List<Hanke>
}

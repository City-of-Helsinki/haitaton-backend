package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.CableReportWithoutHanke
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.SavedHanke

interface HankeService {

    fun loadHanke(hankeTunnus: String): SavedHanke?

    fun findIdentifier(hankeTunnus: String): HankeIdentifier?

    fun getHankeApplications(hankeTunnus: String): List<Application>

    fun createHanke(request: CreateHankeRequest): SavedHanke

    fun generateHankeWithApplication(
        cableReport: CableReportWithoutHanke,
        userId: String
    ): Application

    fun updateHanke(hanke: SavedHanke): SavedHanke

    fun deleteHanke(hankeTunnus: String, userId: String)

    fun loadPublicHanke(): List<SavedHanke>

    fun loadHankeById(id: Int): SavedHanke?

    fun loadHankkeetByIds(ids: List<Int>): List<SavedHanke>
}

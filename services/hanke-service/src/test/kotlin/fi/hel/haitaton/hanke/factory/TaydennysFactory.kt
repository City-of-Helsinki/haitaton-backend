package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusEntityData
import fi.hel.haitaton.hanke.taydennys.Taydennys
import fi.hel.haitaton.hanke.taydennys.TaydennysEntity
import fi.hel.haitaton.hanke.taydennys.TaydennysRepository
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoEntity
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class TaydennysFactory(
    private val taydennysRepository: TaydennysRepository,
    private val taydennyspyyntoFactory: TaydennyspyyntoFactory,
) {
    fun save(
        id: UUID = DEFAULT_ID,
        applicationId: Long? = null,
        taydennyspyynto: TaydennyspyyntoEntity =
            applicationId?.let { taydennyspyyntoFactory.saveEntity(it) }
                ?: throw RuntimeException(),
        hakemusData: HakemusEntityData = ApplicationFactory.createCableReportApplicationData()
    ): Taydennys =
        TaydennysEntity(id, taydennyspyynto, hakemusData)
            .let { taydennysRepository.save(it) }
            .toDomain()

    companion object {
        val DEFAULT_ID: UUID = UUID.fromString("49ee9168-a1e3-45a1-8fe0-9330cd5475d3")

        fun create(
            id: UUID = DEFAULT_ID,
            taydennyspyyntoId: UUID = TaydennyspyyntoFactory.DEFAULT_ID,
            hakemusData: HakemusData = HakemusFactory.createJohtoselvityshakemusData()
        ) = Taydennys(id, taydennyspyyntoId, hakemusData)
    }
}

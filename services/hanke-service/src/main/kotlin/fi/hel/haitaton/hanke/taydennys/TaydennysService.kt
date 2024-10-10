package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.hakemus.HakemusIdentifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TaydennysService(
    private val taydennyspyyntoRepository: TaydennyspyyntoRepository,
    private val alluClient: AlluClient,
) {
    @Transactional(readOnly = true)
    fun findTaydennyspyynto(hakemusId: Long): Taydennyspyynto? =
        taydennyspyyntoRepository.findByApplicationId(hakemusId)?.toDomain()

    @Transactional
    fun saveTaydennyspyyntoFromAllu(hakemus: HakemusIdentifier) {
        val request = alluClient.getInformationRequest(hakemus.alluid!!)

        val entity =
            TaydennyspyyntoEntity(
                applicationId = hakemus.id,
                alluId = request.informationRequestId,
                kentat =
                    request.fields
                        .associate { it.fieldKey to it.requestDescription }
                        .toMutableMap(),
            )

        taydennyspyyntoRepository.save(entity)
    }
}

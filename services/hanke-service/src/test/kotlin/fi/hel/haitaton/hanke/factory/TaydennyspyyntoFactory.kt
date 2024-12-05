package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.InformationRequestFieldKey
import fi.hel.haitaton.hanke.taydennys.Taydennyspyynto
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoEntity
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoRepository
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class TaydennyspyyntoFactory(private val taydennyspyyntoRepository: TaydennyspyyntoRepository) {

    fun save(
        applicationId: Long,
        alluId: Int = DEFAULT_ALLU_ID,
        kentat: Map<InformationRequestFieldKey, String> = DEFAULT_KENTAT,
        mutator: TaydennyspyyntoEntity.() -> Unit = {},
    ): Taydennyspyynto = saveEntity(applicationId, alluId, kentat, mutator).toDomain()

    fun saveEntity(
        applicationId: Long,
        alluId: Int = DEFAULT_ALLU_ID,
        kentat: Map<InformationRequestFieldKey, String> = DEFAULT_KENTAT,
        mutator: TaydennyspyyntoEntity.() -> Unit = {},
    ): TaydennyspyyntoEntity {
        val entity =
            TaydennyspyyntoEntity(
                applicationId = applicationId,
                alluId = alluId,
                kentat = kentat.toMutableMap(),
            )
        mutator(entity)
        return taydennyspyyntoRepository.save(entity)
    }

    companion object {
        val DEFAULT_ID: UUID = UUID.fromString("aa22af88-213e-4d82-869e-c3166a191ce5")
        const val DEFAULT_ALLU_ID: Int = 4141
        val DEFAULT_KENTAT: Map<InformationRequestFieldKey, String> =
            mapOf(
                InformationRequestFieldKey.CUSTOMER to "Customer is missing",
                InformationRequestFieldKey.ATTACHMENT to "Needs a letter of attorney",
            )

        fun create(
            id: UUID = DEFAULT_ID,
            hakemusId: Long = ApplicationFactory.DEFAULT_APPLICATION_ID,
            kentat: Map<InformationRequestFieldKey, String> = DEFAULT_KENTAT,
        ): Taydennyspyynto = Taydennyspyynto(id = id, hakemusId = hakemusId, kentat = kentat)

        fun TaydennyspyyntoEntity.addKentta(key: InformationRequestFieldKey, message: String) =
            kentat.put(key, message)

        fun TaydennyspyyntoEntity.clearKentat() {
            kentat.clear()
        }

        fun createEntity(
            id: UUID = DEFAULT_ID,
            applicationId: Long = ApplicationFactory.DEFAULT_APPLICATION_ID,
            alluId: Int = DEFAULT_ALLU_ID,
            kentat: Map<InformationRequestFieldKey, String> = DEFAULT_KENTAT,
        ) = TaydennyspyyntoEntity(id, applicationId, alluId, kentat.toMutableMap())
    }
}

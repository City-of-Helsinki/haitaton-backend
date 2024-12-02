package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import java.util.UUID

interface TaydennysIdentifier : HasId<UUID> {
    override val id: UUID

    fun taydennyspyyntoId(): UUID

    fun taydennyspyyntoAlluId(): Int

    fun hakemusId(): Long

    fun hakemustyyppi(): ApplicationType

    fun logString() =
        "Täydennys: (" +
            listOf(
                    "id=$id",
                    "täydennyspyyntö=${taydennyspyyntoId()}",
                    "täydennyspyyntöAlluId=${taydennyspyyntoAlluId()}",
                    "hakemusId=${hakemusId()}",
                    "hakemustyyppi=${hakemustyyppi()}")
                .joinToString() +
            ")"
}

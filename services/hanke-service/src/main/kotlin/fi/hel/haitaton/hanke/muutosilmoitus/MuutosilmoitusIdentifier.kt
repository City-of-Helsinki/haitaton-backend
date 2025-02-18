package fi.hel.haitaton.hanke.muutosilmoitus

import fi.hel.haitaton.hanke.domain.HasId
import java.util.UUID

interface MuutosilmoitusIdentifier : HasId<UUID> {
    override val id: UUID
    val hakemusId: Long

    fun logString() = "Muutosilmoitus: (id=$id, hakemusId=$hakemusId)"
}

package fi.hel.haitaton.hanke.muutosilmoitus

import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.domain.Loggable
import java.util.UUID

interface MuutosilmoitusIdentifier : HasId<UUID>, Loggable {
    override val id: UUID
    val hakemusId: Long

    override fun logString() = "Muutosilmoitus: (id=$id, hakemusId=$hakemusId)"
}

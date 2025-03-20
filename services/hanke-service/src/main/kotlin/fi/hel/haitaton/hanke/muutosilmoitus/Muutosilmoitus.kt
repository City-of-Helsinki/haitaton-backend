package fi.hel.haitaton.hanke.muutosilmoitus

import fi.hel.haitaton.hanke.attachment.muutosilmoitus.MuutosilmoitusAttachmentMetadata
import fi.hel.haitaton.hanke.hakemus.HakemusData
import java.time.OffsetDateTime
import java.util.UUID

data class Muutosilmoitus(
    override val id: UUID,
    override val hakemusId: Long,
    val sent: OffsetDateTime?,
    val hakemusData: HakemusData,
) : MuutosilmoitusIdentifier {
    fun toResponse() = MuutosilmoitusResponse(id, sent, hakemusData.toResponse())

    fun withExtras(
        otherData: HakemusData,
        liitteet: List<MuutosilmoitusAttachmentMetadata>,
    ): MuutosilmoitusWithExtras {
        return MuutosilmoitusWithExtras(
            id = id,
            hakemusId = hakemusId,
            sent = sent,
            hakemusData = hakemusData,
            muutokset = hakemusData.listChanges(otherData),
            liitteet = liitteet,
        )
    }
}

data class MuutosilmoitusWithExtras(
    override val id: UUID,
    override val hakemusId: Long,
    val sent: OffsetDateTime?,
    val hakemusData: HakemusData,
    val muutokset: List<String>,
    val liitteet: List<MuutosilmoitusAttachmentMetadata>,
) : MuutosilmoitusIdentifier {
    fun toResponse() =
        MuutosilmoitusResponse(id, sent, hakemusData.toResponse()).withExtras(muutokset, liitteet)
}

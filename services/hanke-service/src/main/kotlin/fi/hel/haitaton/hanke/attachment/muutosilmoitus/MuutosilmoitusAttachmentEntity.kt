package fi.hel.haitaton.hanke.attachment.muutosilmoitus

import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "muutosilmoituksen_liite")
class MuutosilmoitusAttachmentEntity(
    id: UUID?,
    fileName: String,
    contentType: String,
    size: Long,
    blobLocation: String,
    createdByUserId: String,
    createdAt: OffsetDateTime,
    @Column(name = "muutosilmoitus_id") var muutosilmoitusId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type")
    var attachmentType: ApplicationAttachmentType,
) : AttachmentEntity(id, fileName, contentType, size, blobLocation, createdByUserId, createdAt) {

    fun toDomain(): MuutosilmoitusAttachmentMetadata =
        MuutosilmoitusAttachmentMetadata(
            id = id!!,
            fileName = fileName,
            contentType = contentType,
            size = size,
            attachmentType = attachmentType,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            blobLocation = blobLocation,
            muutosilmoitusId = muutosilmoitusId,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as MuutosilmoitusAttachmentEntity

        return id == other.id
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }
}

package fi.hel.haitaton.hanke.liitteet

import fi.hel.haitaton.hanke.HankeEntity
import java.time.LocalDateTime
import java.util.UUID
import javax.persistence.Basic
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.validation.constraints.NotNull
import org.hibernate.annotations.Type

@Entity
@Table(name = "attachment")
class HankeAttachmentEntity(
    @Id @GeneratedValue var id: UUID? = null,
    @Column(nullable = false) var name: String,
    @Lob
    @Type(type = "org.hibernate.type.BinaryType")
    @Basic(fetch = FetchType.LAZY)
    @NotNull
    var content: ByteArray,
    @Column(updatable = false, nullable = false) var createdByUserId: String,
    @Column(updatable = false, nullable = false) var createdAt: LocalDateTime,
    @ManyToOne(cascade = []) var hanke: HankeEntity,
    @Enumerated(EnumType.STRING) var scanStatus: AttachmentScanStatus = AttachmentScanStatus.PENDING
) {
    fun toAttachment(): AttachmentMetadata {
        return AttachmentMetadata(
            id = id,
            name = name,
            createdAt = createdAt,
            scanStatus = scanStatus,
            hankeTunnus = hanke.hankeTunnus!!,
            createdByUserId = createdByUserId,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HankeAttachmentEntity

        if (id != other.id) return false
        if (name != other.name) return false
        if (!content.contentEquals(other.content)) return false
        if (createdByUserId != other.createdByUserId) return false
        if (createdAt != other.createdAt) return false
        if (hanke != other.hanke) return false
        if (scanStatus != other.scanStatus) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + name.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + createdByUserId.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + hanke.hashCode()
        result = 31 * result + scanStatus.hashCode()
        return result
    }
}

enum class AttachmentScanStatus {
    PENDING,
    FAILED,
    OK
}

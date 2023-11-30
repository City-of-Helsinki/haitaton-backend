package fi.hel.haitaton.hanke.attachment.common

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.Attachment
import fi.hel.haitaton.hanke.allu.AttachmentMetadata
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@MappedSuperclass
abstract class AttachmentEntity(
    @Id @GeneratedValue var id: UUID? = null,

    /** Attachment file name. */
    @Column(name = "file_name", nullable = false) var fileName: String,

    /** File type, e.g. application/pdf. */
    @Column(name = "content_type") var contentType: String,

    /** Person who uploaded this attachment. */
    @Column(name = "created_by_user_id", updatable = false, nullable = false)
    var createdByUserId: String,

    /** Creation timestamp. */
    @Column(name = "created_at", updatable = false, nullable = false) var createdAt: OffsetDateTime,

    /** Location of the file in Azure Blob. */
    @Column(name = "blob_location") var blobLocation: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttachmentEntity

        return id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }
}

@Entity
@Table(name = "hanke_attachment")
class HankeAttachmentEntity(
    id: UUID?,
    fileName: String,
    contentType: String,
    createdByUserId: String,
    createdAt: OffsetDateTime,
    blobLocation: String?,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "hanke_id") var hanke: HankeEntity,
) : AttachmentEntity(id, fileName, contentType, createdByUserId, createdAt, blobLocation) {
    fun toDomain(): HankeAttachment {
        return HankeAttachment(
            id = id!!,
            fileName = fileName,
            createdAt = createdAt,
            hankeTunnus = hanke.hankeTunnus,
            createdByUserId = createdByUserId,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as HankeAttachmentEntity

        return id == other.id
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + hanke.hashCode()
        return result
    }
}

@Entity
@Table(name = "application_attachment")
class ApplicationAttachmentEntity(
    id: UUID?,
    fileName: String,
    contentType: String,
    createdByUserId: String,
    createdAt: OffsetDateTime,
    blobLocation: String?,
    @Column(name = "application_id") var applicationId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type")
    var attachmentType: ApplicationAttachmentType,
) : AttachmentEntity(id, fileName, contentType, createdByUserId, createdAt, blobLocation) {
    fun toDto(): ApplicationAttachmentMetadata {
        return ApplicationAttachmentMetadata(
            id = id!!,
            fileName = fileName,
            createdAt = createdAt,
            createdByUserId = createdByUserId,
            applicationId = applicationId,
            attachmentType = attachmentType,
        )
    }

    fun toAlluAttachment(content: ByteArray): Attachment {
        return Attachment(
            metadata =
                AttachmentMetadata(
                    id = null,
                    mimeType = contentType,
                    name = fileName,
                    description = null,
                ),
            file = content
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ApplicationAttachmentEntity

        return id == other.id
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + attachmentType.hashCode()
        result = 31 * result + applicationId.hashCode()
        return result
    }
}

@Repository
interface HankeAttachmentRepository : JpaRepository<HankeAttachmentEntity, UUID> {
    fun countByHankeId(hankeId: Int): Int
}

@Repository
interface ApplicationAttachmentRepository : JpaRepository<ApplicationAttachmentEntity, UUID> {
    fun findByApplicationId(id: Long): List<ApplicationAttachmentEntity>

    fun findByApplicationIdAndId(applicationId: Long, id: UUID): ApplicationAttachmentEntity?

    fun countByApplicationId(applicationId: Long): Int
}

enum class ApplicationAttachmentType {
    MUU,
    LIIKENNEJARJESTELY,
    VALTAKIRJA,
}

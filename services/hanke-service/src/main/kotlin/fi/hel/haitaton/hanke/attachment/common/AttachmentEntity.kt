package fi.hel.haitaton.hanke.attachment.common

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.Attachment
import fi.hel.haitaton.hanke.allu.AttachmentMetadata
import fi.hel.haitaton.hanke.application.ApplicationEntity
import java.time.OffsetDateTime
import java.util.UUID
import javax.persistence.Basic
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.Lob
import javax.persistence.ManyToOne
import javax.persistence.MappedSuperclass
import javax.persistence.Table
import javax.validation.constraints.NotNull
import org.hibernate.annotations.Type
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@MappedSuperclass
abstract class AttachmentEntity(
    @Id @GeneratedValue var id: UUID? = null,

    /** Attachment file name. */
    @Column(name = "file_name", nullable = false) var fileName: String,

    /** Attachment data, i.e. the file itself. */
    @Lob
    @Type(type = "org.hibernate.type.BinaryType")
    @Basic(fetch = FetchType.LAZY)
    @NotNull
    var content: ByteArray,

    /** File type, e.g. application/pdf. */
    @Column(name = "content_type") var contentType: String,

    /** Person who uploaded this attachment. */
    @Column(name = "created_by_user_id", updatable = false, nullable = false)
    var createdByUserId: String,

    /** Creation timestamp. */
    @Column(name = "created_at", updatable = false, nullable = false) var createdAt: OffsetDateTime,

    /** Virus scan status. Possible values: PENDING, FAILED, OK. */
    @Column(name = "scan_status")
    @Enumerated(EnumType.STRING)
    var scanStatus: AttachmentScanStatus = AttachmentScanStatus.PENDING
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttachmentEntity

        return when {
            fileName != other.fileName -> false
            createdByUserId != other.createdByUserId -> false
            createdAt != other.createdAt -> false
            scanStatus != other.scanStatus -> false
            else -> true
        }
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + createdByUserId.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + scanStatus.hashCode()
        return result
    }
}

@Entity
@Table(name = "hanke_attachment")
class HankeAttachmentEntity(
    id: UUID?,
    fileName: String,
    content: ByteArray,
    contentType: String,
    createdByUserId: String,
    createdAt: OffsetDateTime,
    scanStatus: AttachmentScanStatus,

    /** Hanke in which this attachment belongs to. */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "hanke_id") var hanke: HankeEntity,
) : AttachmentEntity(id, fileName, content, contentType, createdByUserId, createdAt, scanStatus) {
    fun toMetadata(): HankeAttachmentMetadata {
        return HankeAttachmentMetadata(
            id = id,
            fileName = fileName,
            createdAt = createdAt,
            scanStatus = scanStatus,
            hankeTunnus = hanke.hankeTunnus!!,
            createdByUserId = createdByUserId,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as HankeAttachmentEntity

        if (hanke != other.hanke) return false

        return true
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
    content: ByteArray,
    contentType: String,
    createdByUserId: String,
    createdAt: OffsetDateTime,
    scanStatus: AttachmentScanStatus,

    /** Attachment type: MUU, LIIKENNEJARJESTELY, VALTAKIRJA. */
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type")
    var attachmentType: ApplicationAttachmentType,

    /** Hanke in which this attachment belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    var application: ApplicationEntity,
) : AttachmentEntity(id, fileName, content, contentType, createdByUserId, createdAt, scanStatus) {
    fun toMetadata(): ApplicationAttachmentMetadata {
        return ApplicationAttachmentMetadata(
            id = id,
            fileName = fileName,
            createdAt = createdAt,
            scanStatus = scanStatus,
            createdByUserId = createdByUserId,
            applicationId = application.id!!,
            attachmentType = attachmentType,
        )
    }

    fun toAlluAttachment(): Attachment {
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

        if (attachmentType != other.attachmentType) return false
        if (application != other.application) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + attachmentType.hashCode()
        result = 31 * result + application.hashCode()
        return result
    }
}

@Repository
interface HankeAttachmentRepository : JpaRepository<HankeAttachmentEntity, UUID> {
    @Modifying
    @Query("DELETE FROM HankeAttachmentEntity WHERE id = :id")
    fun deleteAttachment(id: UUID)
}

@Repository
interface ApplicationAttachmentRepository : JpaRepository<ApplicationAttachmentEntity, UUID> {
    @Modifying
    @Query("DELETE FROM ApplicationAttachmentEntity WHERE id = :id")
    fun deleteAttachment(id: UUID)
}

enum class ApplicationAttachmentType {
    MUU,
    LIIKENNEJARJESTELY,
    VALTAKIRJA,
}

enum class AttachmentScanStatus {
    PENDING,
    FAILED,
    OK
}
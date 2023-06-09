package fi.hel.haitaton.hanke.attachment.common

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.Attachment
import fi.hel.haitaton.hanke.allu.AttachmentMetadata
import java.time.OffsetDateTime
import java.util.UUID
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
import org.springframework.data.repository.query.Param
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

    /** Virus scan status. Possible values: PENDING, FAILED, OK. */
    @Column(name = "scan_status")
    @Enumerated(EnumType.STRING)
    var scanStatus: AttachmentScanStatus = AttachmentScanStatus.PENDING
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
    scanStatus: AttachmentScanStatus,

    /** Attachment data, i.e. the file itself. */
    @Lob @Type(type = "org.hibernate.type.BinaryType") @NotNull var content: ByteArray,

    /** Hanke in which this attachment belongs to. */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "hanke_id") var hanke: HankeEntity,
) : AttachmentEntity(id, fileName, contentType, createdByUserId, createdAt, scanStatus) {
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
class ApplicationAttachmentSummary(
    id: UUID?,
    fileName: String,
    contentType: String,
    createdByUserId: String,
    createdAt: OffsetDateTime,
    scanStatus: AttachmentScanStatus,

    /** Application of this attachment. */
    @Column(name = "application_id") var applicationId: Long,

    /** Attachment type: MUU, LIIKENNEJARJESTELY, VALTAKIRJA. */
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type")
    var attachmentType: ApplicationAttachmentType,
) : AttachmentEntity(id, fileName, contentType, createdByUserId, createdAt, scanStatus) {
    fun toDto(): ApplicationAttachmentMetadata {
        return ApplicationAttachmentMetadata(
            id = id,
            fileName = fileName,
            createdAt = createdAt,
            scanStatus = scanStatus,
            createdByUserId = createdByUserId,
            applicationId = applicationId,
            attachmentType = attachmentType,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ApplicationAttachmentSummary

        return id == other.id
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + attachmentType.hashCode()
        result = 31 * result + applicationId.hashCode()
        return result
    }
}

@Entity
@Table(name = "application_attachment")
class ApplicationAttachmentContent(
    id: UUID?,
    fileName: String,
    contentType: String,
    createdByUserId: String,
    createdAt: OffsetDateTime,
    scanStatus: AttachmentScanStatus,

    /** Application of this attachment. */
    @Column(name = "application_id") var applicationId: Long,

    /** Attachment data, i.e. the file itself. */
    @Lob @Type(type = "org.hibernate.type.BinaryType") @NotNull var content: ByteArray,

    /** Attachment type: MUU, LIIKENNEJARJESTELY, VALTAKIRJA. */
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type")
    var attachmentType: ApplicationAttachmentType,
) : AttachmentEntity(id, fileName, contentType, createdByUserId, createdAt, scanStatus) {

    fun toMetadata(): ApplicationAttachmentMetadata =
        ApplicationAttachmentMetadata(
            id = id,
            fileName = fileName,
            createdAt = createdAt,
            scanStatus = scanStatus,
            createdByUserId = createdByUserId,
            applicationId = applicationId,
            attachmentType = attachmentType,
        )

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

        other as ApplicationAttachmentSummary

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
    @Modifying
    @Query("DELETE FROM HankeAttachmentEntity WHERE id = :id")
    fun deleteAttachment(id: UUID)
}

@Repository
interface ApplicationAttachmentSummaryRepository :
    JpaRepository<ApplicationAttachmentSummary, UUID> {
    @Modifying
    @Query("DELETE FROM ApplicationAttachmentSummary WHERE id = :id")
    fun deleteAttachment(id: UUID)

    fun findByApplicationId(id: Long): List<ApplicationAttachmentSummary>

    @Query("SELECT a FROM ApplicationAttachmentSummary a WHERE a.id = :id")
    fun summaryById(@Param("id") id: UUID): ApplicationAttachmentSummary
}

@Repository
interface ApplicationAttachmentContentRepository :
    JpaRepository<ApplicationAttachmentContent, UUID> {

    @Query("SELECT a FROM ApplicationAttachmentContent a WHERE a.id = :id")
    fun contentById(@Param("id") id: UUID): ApplicationAttachmentContent
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

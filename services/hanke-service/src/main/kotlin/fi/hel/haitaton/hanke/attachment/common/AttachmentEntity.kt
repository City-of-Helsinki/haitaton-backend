package fi.hel.haitaton.hanke.attachment.common

import fi.hel.haitaton.hanke.HankeEntity
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

    /** Size in bytes. */
    @Column(name = "size") var size: Long,

    /** Path to the blob in Azure. */
    @Column(name = "blob_location") var blobLocation: String,

    /** Person who uploaded this attachment. */
    @Column(name = "created_by_user_id", updatable = false, nullable = false)
    var createdByUserId: String,

    /** Creation timestamp. */
    @Column(name = "created_at", updatable = false, nullable = false) var createdAt: OffsetDateTime,
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
    size: Long,
    blobLocation: String,
    createdByUserId: String,
    createdAt: OffsetDateTime,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "hanke_id") var hanke: HankeEntity,
) : AttachmentEntity(id, fileName, contentType, size, blobLocation, createdByUserId, createdAt) {
    fun toDto(): HankeAttachmentMetadataDto =
        HankeAttachmentMetadataDto(
            id = id!!,
            fileName = fileName,
            contentType = contentType,
            size = size,
            createdAt = createdAt,
            hankeTunnus = hanke.hankeTunnus,
            createdByUserId = createdByUserId,
        )

    fun toDomain(): HankeAttachmentMetadata =
        HankeAttachmentMetadata(
            id!!,
            fileName,
            contentType,
            size,
            createdByUserId,
            createdAt,
            blobLocation,
            hanke.id,
        )

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
    size: Long,
    blobLocation: String,
    createdByUserId: String,
    createdAt: OffsetDateTime,
    @Column(name = "application_id") var applicationId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type")
    var attachmentType: ApplicationAttachmentType,
) : AttachmentEntity(id, fileName, contentType, size, blobLocation, createdByUserId, createdAt) {

    fun toDomain(): ApplicationAttachmentMetadata =
        ApplicationAttachmentMetadata(
            id = id!!,
            fileName = fileName,
            contentType = contentType,
            size = size,
            attachmentType = attachmentType,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            blobLocation = blobLocation,
            applicationId = applicationId,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ApplicationAttachmentEntity

        return id == other.id
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }
}

@Entity
@Table(name = "taydennys_liite")
class TaydennysAttachmentEntity(
    id: UUID?,
    fileName: String,
    contentType: String,
    size: Long,
    blobLocation: String,
    createdByUserId: String,
    createdAt: OffsetDateTime,
    @Column(name = "taydennys_id") var taydennysId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type")
    var attachmentType: ApplicationAttachmentType,
) : AttachmentEntity(id, fileName, contentType, size, blobLocation, createdByUserId, createdAt) {

    fun toDomain(): TaydennysAttachmentMetadata =
        TaydennysAttachmentMetadata(
            id = id!!,
            fileName = fileName,
            contentType = contentType,
            size = size,
            attachmentType = attachmentType,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            blobLocation = blobLocation,
            taydennysId = taydennysId,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as TaydennysAttachmentEntity

        return id == other.id
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }
}

@Repository
interface HankeAttachmentRepository : JpaRepository<HankeAttachmentEntity, UUID> {
    fun countByHankeId(hankeId: Int): Int
}

@Repository
interface ApplicationAttachmentRepository : JpaRepository<ApplicationAttachmentEntity, UUID> {
    fun findByApplicationId(applicationId: Long): List<ApplicationAttachmentEntity>

    fun countByApplicationId(applicationId: Long): Int

    fun deleteByApplicationId(applicationId: Long)
}

@Repository
interface TaydennysAttachmentRepository : JpaRepository<TaydennysAttachmentEntity, UUID> {
    fun findByTaydennysId(taydennysId: UUID): List<TaydennysAttachmentEntity>

    fun countByTaydennysId(taydennysId: UUID): Int
}

enum class ApplicationAttachmentType {
    MUU,
    LIIKENNEJARJESTELY,
    VALTAKIRJA;

    fun toFinnish(): String =
        when (this) {
            MUU -> "Muu liite"
            LIIKENNEJARJESTELY -> "Liikennejärjestelysuunnitelma"
            VALTAKIRJA -> "Valtakirja"
        }
}

package fi.hel.haitaton.hanke.attachment.common

import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.MappedSuperclass
import javax.persistence.Table
import javax.validation.constraints.NotNull
import org.hibernate.annotations.Type
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@MappedSuperclass
abstract class AttachmentContentEntity(
    @Id @Column(name = "attachment_id") var attachmentId: UUID,

    /** Attachment data, i.e. the file itself. */
    @Lob @Type(type = "org.hibernate.type.BinaryType") @NotNull var content: ByteArray,
)

@Entity
@Table(name = "hanke_attachment_content")
class HankeAttachmentContentEntity(
    attachmentId: UUID,
    content: ByteArray,
) : AttachmentContentEntity(attachmentId, content)

@Entity
@Table(name = "application_attachment_content")
class ApplicationAttachmentContentEntity(
    attachmentId: UUID,
    content: ByteArray,
) : AttachmentContentEntity(attachmentId, content)

@Repository
interface HankeAttachmentContentRepository : JpaRepository<HankeAttachmentContentEntity, UUID>

@Repository
interface ApplicationAttachmentContentRepository :
    JpaRepository<ApplicationAttachmentContentEntity, UUID>

package fi.hel.haitaton.hanke.attachment.common

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import java.sql.Types
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@MappedSuperclass
abstract class AttachmentContentEntity(
    @Id @Column(name = "attachment_id") var attachmentId: UUID,

    /** Attachment data, i.e. the file itself. */
    @Lob @JdbcTypeCode(Types.BINARY) @NotNull var content: ByteArray,
)

@Entity
@Table(name = "application_attachment_content")
class ApplicationAttachmentContentEntity(
    attachmentId: UUID,
    content: ByteArray,
) : AttachmentContentEntity(attachmentId, content)

@Repository
interface ApplicationAttachmentContentRepository :
    JpaRepository<ApplicationAttachmentContentEntity, UUID>

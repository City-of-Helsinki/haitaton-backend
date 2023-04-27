package fi.hel.haitaton.hanke.attachment

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository interface HankeAttachmentRepository : JpaRepository<HankeAttachmentEntity, UUID>

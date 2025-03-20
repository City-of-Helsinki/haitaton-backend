package fi.hel.haitaton.hanke.attachment.muutosilmoitus

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MuutosilmoitusAttachmentRepository : JpaRepository<MuutosilmoitusAttachmentEntity, UUID> {
    fun countByMuutosilmoitusId(muutosilmoitusId: UUID): Int

    fun findByMuutosilmoitusId(muutosilmoitusId: UUID): List<MuutosilmoitusAttachmentEntity>
}

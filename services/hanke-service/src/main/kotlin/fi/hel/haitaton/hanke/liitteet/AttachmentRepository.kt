package fi.hel.haitaton.hanke.liitteet

import fi.hel.haitaton.hanke.HankeEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AttachmentRepository : JpaRepository<HankeAttachmentEntity, UUID> {

    fun findAllByHanke(hanke: HankeEntity): MutableList<HankeAttachmentEntity>
}

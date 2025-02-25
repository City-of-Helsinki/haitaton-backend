package fi.hel.haitaton.hanke.muutosilmoitus

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MuutosilmoitusRepository : JpaRepository<MuutosilmoitusEntity, UUID> {
    fun findByHakemusId(hakemusId: Long): MuutosilmoitusEntity?
}

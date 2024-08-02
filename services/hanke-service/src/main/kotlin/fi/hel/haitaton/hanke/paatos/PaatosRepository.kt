package fi.hel.haitaton.hanke.paatos

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaatosRepository : JpaRepository<PaatosEntity, UUID> {
    fun findByHakemusId(hakemusId: Long): List<PaatosEntity>
}

package fi.hel.haitaton.hanke.paatos

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PaatosRepository : JpaRepository<PaatosEntity, UUID> {
    fun findByHakemusId(hakemusId: Long): List<PaatosEntity>

    @Modifying
    @Query("UPDATE PaatosEntity SET tila = :tila WHERE hakemustunnus = :hakemustunnus")
    fun markReplacedByHakemustunnus(hakemustunnus: String, tila: PaatosTila = PaatosTila.KORVATTU)
}

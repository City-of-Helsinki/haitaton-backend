package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface HakemusRepository : JpaRepository<HakemusEntity, Long> {
    fun findOneById(id: Long): HakemusEntity?

    @Query("select alluid from HakemusEntity where alluid is not null")
    fun getAllAlluIds(): List<Int>

    fun getOneByAlluid(alluid: Int): HakemusEntity?

    @Query(
        """SELECT new kotlin.Pair(a, m)
           FROM HakemusEntity a
           LEFT JOIN MuutosilmoitusEntity m ON m.hakemusId = a.id
           WHERE a.hanke.id = :hankeId"""
    )
    fun findWithMuutosilmoitukset(hankeId: Int): List<Pair<HakemusEntity, MuutosilmoitusEntity?>>
}

package fi.hel.haitaton.hanke.hakemus

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface HakemusRepository : JpaRepository<HakemusEntity, Long> {
    fun findOneById(id: Long): HakemusEntity?

    @Query("select alluid from HakemusEntity where alluid is not null")
    fun getAllAlluIds(): List<Int>

    fun getOneByAlluid(alluid: Int): HakemusEntity?
}

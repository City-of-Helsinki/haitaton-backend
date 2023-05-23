package fi.hel.haitaton.hanke.application

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ApplicationRepository : JpaRepository<ApplicationEntity, Long> {
    fun findOneById(id: Long): ApplicationEntity?

    fun getAllByUserId(userId: String): List<ApplicationEntity>

    @Query("select alluid from ApplicationEntity where alluid is not null")
    fun getAllAlluIds(): List<Int>

    fun getOneByAlluid(alluid: Int): ApplicationEntity?
}

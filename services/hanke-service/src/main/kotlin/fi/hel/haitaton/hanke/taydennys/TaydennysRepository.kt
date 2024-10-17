package fi.hel.haitaton.hanke.taydennys

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface TaydennysRepository : JpaRepository<TaydennysEntity, UUID> {

    @Query(
        "select t from TaydennysEntity t " +
            "inner join t.taydennyspyynto tp " +
            "where tp.applicationId = :hakemusId")
    fun findByApplicationId(hakemusId: Long): TaydennysEntity?
}

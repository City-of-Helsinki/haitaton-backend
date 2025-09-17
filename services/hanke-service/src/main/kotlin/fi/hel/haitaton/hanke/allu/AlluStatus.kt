package fi.hel.haitaton.hanke.allu

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Entity
@Table(name = "allu_status")
class AlluStatus(
    @Id val id: Long,
    @Column(name = "history_last_updated") var historyLastUpdated: ZonedDateTime,
)

@Repository
interface AlluStatusRepository : JpaRepository<AlluStatus, Long> {
    @Query("select historyLastUpdated from AlluStatus") fun getLastUpdateTime(): ZonedDateTime
}

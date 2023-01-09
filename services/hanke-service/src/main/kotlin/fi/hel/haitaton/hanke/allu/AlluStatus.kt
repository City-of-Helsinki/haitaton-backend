package fi.hel.haitaton.hanke.allu

import java.time.OffsetDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Entity
@Table(name = "allu_status")
class AlluStatus(
    @Id val id: Long,
    @Column(name = "history_last_updated") var historyLastUpdated: OffsetDateTime,
)

@Repository
interface AlluStatusRepository : JpaRepository<AlluStatus, Long> {
    @Query("select historyLastUpdated from AlluStatus") fun getLastUpdateTime(): OffsetDateTime
}

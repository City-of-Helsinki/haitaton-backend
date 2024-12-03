package fi.hel.haitaton.hanke.taydennys

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TaydennysyhteyshenkiloRepository : JpaRepository<TaydennysyhteyshenkiloEntity, UUID>

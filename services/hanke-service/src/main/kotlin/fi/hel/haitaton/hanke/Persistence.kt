package fi.hel.haitaton.hanke

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.time.LocalDateTime
import javax.persistence.*

/*
Hibernate/JPA Entity classes
 */

// TODO: remove once everything has been converted to use the new, real entity class
data class OldHankeEntity(val id: String) { }


enum class SaveType {
    AUTO, // When the information has been saved by a periodic auto-save feature
    DRAFT, // When the user presses "saves as draft"
    SUBMIT // When the user presses "save" or "submit" or such that indicates the data is ready.
}

// Build-time plugins will open the class and add no-arg constructor for @Entity classes.

@Entity @Table(name = "hanke")
class HankeEntity (
    @Enumerated(EnumType.STRING)
    var saveType: SaveType? = null,
    var hankeTunnus: String? = null,
    var name: String? = null,
    var startDate: LocalDate? = null, // NOTE: stored and handled in UTC, not "local"
    var endDate: LocalDate? = null, // NOTE: stored and handled in UTC, not "local"
    var owner: String? = null,
    var phase: String? = null,
    var isYKTHanke: Boolean? = false,
    var createdAt: LocalDateTime? = LocalDateTime.now(),
    var modifiedAt: LocalDateTime? = LocalDateTime.now(),
    // NOTE: using IDENTITY (i.e. db does auto-increments, Hibernate reads the result back)
    // can be a performance problem if there is a need to do bulk inserts.
    // Using SEQUENCE would allow getting multiple ids more efficiently.
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
)

interface HankeRepository : JpaRepository<HankeEntity, Long> {
    fun findByHankeTunnus(hankeTunnus: String): HankeEntity?
    // TODO: add any special 'find' etc. functions here, like searching by date range.
}

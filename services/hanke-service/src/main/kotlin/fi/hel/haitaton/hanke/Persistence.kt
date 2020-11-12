package fi.hel.haitaton.hanke

import org.springframework.data.jpa.repository.JpaRepository
import java.sql.Date
import java.sql.Timestamp
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

@Entity @Table(name = "Hanke")
class HankeEntity (
    @Enumerated(EnumType.STRING)
    var saveType: SaveType? = null,
    var hankeTunnus: String? = null,
    var startDate: Date? = null, // TODO: possibly ZonedDateTime, but we don't need the time or timezone...
    var endDate: Date? = null, // TODO: possibly ZonedDateTime, but we don't need the time or timezone...
    var owner: String? = null,
    var phase: String? = null, // TODO: convert to enum, once known, and @Enumerated(EnumType.STRING)
    var isYKTHanke: Boolean? = false,
    var createdAt: Timestamp? = Timestamp.valueOf(LocalDateTime.now()),
    var modifiedAt: Timestamp? = Timestamp.valueOf(LocalDateTime.now()),
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

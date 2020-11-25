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

enum class Vaihe {
    OHJELMOINTI,
    SUUNNITTELU,
    RAKENTAMINEN
}

// Build-time plugins will open the class and add no-arg constructor for @Entity classes.

@Entity @Table(name = "hanke")
class HankeEntity (
        @Enumerated(EnumType.STRING)
        var saveType: SaveType? = null,
        var hankeTunnus: String? = null,
        var nimi: String? = null,
        var kuvaus: String? = null,
        var alkuPvm: LocalDate? = null, // NOTE: stored and handled in UTC, not in "local" time
        var loppuPvm: LocalDate? = null, // NOTE: stored and handled in UTC, not in "local" time
        @Enumerated(EnumType.STRING)
        var vaihe: Vaihe? = null,
        var onYKTHanke: Boolean? = false,
        var version: Int? = 0,
        // NOTE: creatorUserId must be non-null for valid data, but to allow creating instances with
        // no-arg constructor and programming convenience, this class allows it to be null (temporarily).
        var createdByUserId: Int? = null,
        var createdAt: LocalDateTime? = null,
        var modifiedByUserId: Int? = null,
        var modifiedAt: LocalDateTime? = null,
        // NOTE: using IDENTITY (i.e. db does auto-increments, Hibernate reads the result back)
        // can be a performance problem if there is a need to do bulk inserts.
        // Using SEQUENCE would allow getting multiple ids more efficiently.
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        var id: Int? = null
)

interface HankeRepository : JpaRepository<HankeEntity, Int> {
    fun findByHankeTunnus(hankeTunnus: String): HankeEntity?
    // TODO: add any special 'find' etc. functions here, like searching by date range.
}

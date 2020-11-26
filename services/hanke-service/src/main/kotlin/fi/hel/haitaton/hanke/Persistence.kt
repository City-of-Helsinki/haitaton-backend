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

enum class SuunnitteluVaihe {
    YLEIS_TAI_HANKE,
    KATUSUUNNITTELU_TAI_ALUEVARAUS,
    RAKENNUS_TAI_TOTEUTUS,
    TYOMAAN_TAI_HANKKEEN_AIKAINEN
}

enum class TyomaaTyyppi {
    VESI,
    VIEMARI,
    SADEVESI,
    SAHKO,
    TIETOLIIKENNE,
    LIIKENNEVALO,
    YKT,
    ULKOVALAISTUS,
    KAAPPITYO,
    KAUKOLAMPO,
    KAUKOKYLMA,
    KAASUJOHTO,
    KISKOTYO,
    MUU,
    KADUNRAKENNUS,
    KADUN_KUNNOSSAPITO,
    KIINTEISTOLIITTYMA,
    SULKU_TAI_KAIVO,
    UUDISRAKENNUS,
    SANEERAUS,
    AKILLINEN_VIKAKORJAUS,
    VIHERTYO,
    RUNKOLINJA,
    NOSTOTYO,
    MUUTTO,
    PYSAKKITYO,
    KIINTEISTOREMONTTI,
    ULKOMAINOS,
    KUVAUKSET,
    LUMENPUDOTUS,
    YLEISOTILAISUUS,
    VAIHTOLAVA
}

enum class TyomaaKoko {
    SUPPEA_TAI_PISTE,
    YLI_10M_TAI_KORTTELI,
    LAAJA_TAI_USEA_KORTTELI
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
        var suunnitteluVaihe: SuunnitteluVaihe? = null,
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
) {
        // --------------- Hankkeen lisätiedot / Työmaan tiedot -------------------
        var tyomaaKatuosoite: String? = null
        @ElementCollection(fetch = FetchType.EAGER)
        @CollectionTable(name = "hanketyomaatyyppi", joinColumns = arrayOf(JoinColumn(name = "hankeid")))
        @Enumerated(EnumType.STRING)
        var tyomaaTyyppi: MutableSet<TyomaaTyyppi> = mutableSetOf()
        var tyomaaKoko: TyomaaKoko? = null
}


interface HankeRepository : JpaRepository<HankeEntity, Int> {
    fun findByHankeTunnus(hankeTunnus: String): HankeEntity?
    // TODO: add any special 'find' etc. functions here, like searching by date range.
}

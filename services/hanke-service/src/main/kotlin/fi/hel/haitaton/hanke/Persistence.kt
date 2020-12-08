package fi.hel.haitaton.hanke

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.time.LocalDateTime
import javax.persistence.*

/*
Hibernate/JPA Entity classes
 */

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

enum class Haitta04 {
    EI_VAIKUTA,
    YKSI,
    KAKSI,
    KOLME,
    NELJA
}

enum class Haitta13 {
    YKSI,
    KAKSI,
    KOLME
}


// Build-time plugins will open the class and add no-arg constructor for @Entity classes.

@Entity
@Table(name = "hanke")
class HankeEntity(
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
        var id: Int? = null,

        // related
        // orphanRemoval is needed for even explicit child-object removal. JPA weirdness...
        @OneToMany(fetch = FetchType.LAZY, mappedBy = "hanke", cascade = [CascadeType.ALL], orphanRemoval = true)
        var listOfHankeYhteystieto: MutableList<HankeYhteystietoEntity> = mutableListOf()

) {
    // --------------- Hankkeen lisätiedot / Työmaan tiedot -------------------
    var tyomaaKatuosoite: String? = null

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hanketyomaatyyppi", joinColumns = [JoinColumn(name = "hankeid")])
    @Enumerated(EnumType.STRING)
    var tyomaaTyyppi: MutableSet<TyomaaTyyppi> = mutableSetOf()

    @Enumerated(EnumType.STRING)
    var tyomaaKoko: TyomaaKoko? = null

    // --------------- Hankkeen haitat -------------------
    var haittaAlkuPvm: LocalDate? = null // NOTE: stored and handled in UTC, not in "local" time
    var haittaLoppuPvm: LocalDate? = null // NOTE: stored and handled in UTC, not in "local" time

    // These five fields have generic string values, so can just as well store them with the ordinal number.
    var kaistaHaitta: Haitta04? = null
    var kaistaPituusHaitta: Haitta04? = null
    var meluHaitta: Haitta13? = null
    var polyHaitta: Haitta13? = null
    var tarinaHaitta: Haitta13? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HankeEntity) return false

        if (saveType != other.saveType) return false
        if (hankeTunnus != other.hankeTunnus) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = saveType?.hashCode() ?: 0
        result = 31 * result + (hankeTunnus?.hashCode() ?: 0)
        result = 31 * result + (id ?: 0)
        return result
    }

}

interface HankeRepository : JpaRepository<HankeEntity, Int> {
    fun findByHankeTunnus(hankeTunnus: String): HankeEntity?
    // TODO: add any special 'find' etc. functions here, like searching by date range.
}

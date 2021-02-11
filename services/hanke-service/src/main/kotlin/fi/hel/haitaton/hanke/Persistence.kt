package fi.hel.haitaton.hanke

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
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
    var createdByUserId: String? = null,
    var createdAt: LocalDateTime? = null,
    var modifiedByUserId: String? = null,
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

    // --------------- State flags -------------------
    // NOTE: need to be careful with these to not end up with inconsistent database state.
    // All of these should be resolved automatically, i.e. there are no "setters" for them.
    // They are saved to database in order to reduce processing overhead e.g. when fetching
    // lots of Hanke-objects for showing a list.
    var tilaOnGeometrioita: Boolean? = false
    var tilaOnKaikkiPakollisetLuontiTiedot: Boolean? = false
    var tilaOnTiedotLiikHaittaIndeksille: Boolean? = false
    var tilaOnLiikHaittaIndeksi: Boolean? = false
    var tilaOnViereisiaHankkeita: Boolean? = false
    var tilaOnAsiakasryhmia: Boolean? = false

    // ---------------  Helper functions -----------------

    fun addYhteystieto(yhteystieto: HankeYhteystietoEntity) {
        listOfHankeYhteystieto.add(yhteystieto)
        yhteystieto.hanke = this
    }

    fun removeYhteystieto(yhteystieto: HankeYhteystietoEntity) {
        // NOTE: this relies on equals() to match yhteystietos almost fully.
        if (listOfHankeYhteystieto.contains(yhteystieto)) {
            listOfHankeYhteystieto.remove(yhteystieto)
            yhteystieto.hanke = null
        }
    }

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

    override fun findAll(): List<HankeEntity>

    // search with date range
    fun findAllByAlkuPvmIsBeforeAndLoppuPvmIsAfter(endAlkuPvm: LocalDate, startLoppuPvm: LocalDate): List<HankeEntity>

    //search with saveType
    fun findAllBySaveType(saveType: SaveType): List<HankeEntity>

    /*
        // search with date range, example with query:
        @Query("select h from HankeEntity h "+
                " where (alkupvm >= :periodBegin and alkupvm <= :periodEnd) " +
                " or (loppupvm >= :periodBegin and loppupvm <= :periodEnd) " +
                " or (alkupvm <= :periodBegin and loppupvm >= :periodEnd)")
        fun getAllDataHankeBetweenTimePeriod(periodBegin: LocalDate, periodEnd: LocalDate): List<HankeEntity>
    */

}

enum class CounterType {
    HANKETUNNUS
}

@Entity
@Table(name = "idcounter")
class IdCounter(
    @Id
    @Enumerated(EnumType.STRING)
    var counterType: CounterType? = null,
    var value: Long? = null
)

interface IdCounterRepository : JpaRepository<IdCounter, CounterType> {
    /*
    Basic principals:
    - if current year is the same as before (in column 'year') return incrementing value
    - if current year is not the same as before return 1
    This SQL clause has some PostgreSQL specific thingies:
    'WITH' clause describes a 'variable' table used inside query in two places
    'FOR UPDATE' in nested SELECT clause makes sure that no other process can update the row during this whole UPDATE clause
    'RETURNING' in the end is for UPDATE clase to return not just the number of affected rows but also the column data of those rows (a single row in our case)
    With these specialities we can assure that concurrent calls for this method will never return duplicate values.
    Notice also that the method returns a list even though there is always only max. 1 item in it because counterType is PK.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
            WITH currentyear AS (SELECT EXTRACT(YEAR FROM now() AT TIME ZONE 'UTC'))
            UPDATE 
                idcounter
            SET
                value = CASE
                    WHEN year = currentyear.date_part THEN (SELECT value FROM IdCounter WHERE counterType = :counterType FOR UPDATE) + 1
                    ELSE 1 
                END,
                year = currentyear.date_part
            FROM currentyear
            WHERE counterType = :counterType
            RETURNING counterType, value
            """,
        nativeQuery = true
    )
    fun incrementAndGet(counterType: String): List<IdCounter>
}
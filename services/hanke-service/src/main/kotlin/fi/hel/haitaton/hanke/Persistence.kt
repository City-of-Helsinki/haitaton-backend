package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.tormaystarkastelu.Luokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulosEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

enum class HankeStatus {
    /** A hanke is a draft from its creation until all mandatory fields have been filled. */
    DRAFT,
    /**
     * A hanke goes public after all mandatory fields have been filled. This happens automatically
     * on any update. A public hanke has some info visible to everyone and applications can be added
     * to it.
     */
    PUBLIC,
    /**
     * After the end dates of all hankealue have passed, a hanke is considered finished. It's
     * anonymized and at least mostly hidden in the UI.
     */
    ENDED,
}

enum class Vaihe {
    OHJELMOINTI,
    SUUNNITTELU,
    RAKENTAMINEN
}

enum class TyomaaTyyppi {
    VESI,
    VIEMARI,
    SADEVESI,
    SAHKO,
    TIETOLIIKENNE,
    LIIKENNEVALO,
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

/** NOTE Järjestys täytyy olla pienimmästä suurimpaan */
enum class TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin(
    override val value: Int,
    override val explanation: String
) : Luokittelu {
    YKSI(1, "Ei vaikuta"),
    KAKSI(2, "Vähentää kaistan yhdellä ajosuunnalla"),
    KOLME(3, "Vähentää samanaikaisesti kaistan kahdella ajosuunnalla"),
    NELJA(4, "Vähentää samanaikaisesti useita kaistoja kahdella ajosuunnalla"),
    VIISI(5, "Vähentää samanaikaisesti useita kaistoja liittymien eri suunnilla")
}

/** NOTE Järjestys täytyy olla pienimmästä suurimpaan */
enum class KaistajarjestelynPituus(override val value: Int, override val explanation: String) :
    Luokittelu {
    YKSI(1, "Ei tarvita"),
    KAKSI(2, "Enintään 10 m"),
    KOLME(3, "11 - 100 m"),
    NELJA(4, "101 - 500 m"),
    VIISI(5, "Yli 500 m")
}

/** NOTE Järjestys täytyy olla pienimmästä suurimpaan */
enum class Haitta13 {
    YKSI,
    KAKSI,
    KOLME
}

// Build-time plugins will open the class and add no-arg constructor for @Entity classes.

@Entity
@Table(name = "hanke")
class HankeEntity(
    @Enumerated(EnumType.STRING) var status: HankeStatus = HankeStatus.DRAFT,
    override val hankeTunnus: String,
    var nimi: String,
    var kuvaus: String? = null,
    @Enumerated(EnumType.STRING) var vaihe: Vaihe? = null,
    var onYKTHanke: Boolean? = false,
    var version: Int? = 0,
    // NOTE: creatorUserId must be non-null for valid data, but to allow creating instances with
    // no-arg constructor and programming convenience, this class allows it to be null
    // (temporarily).
    var createdByUserId: String? = null,
    var createdAt: LocalDateTime? = null,
    var modifiedByUserId: String? = null,
    var modifiedAt: LocalDateTime? = null,
    var generated: Boolean = false,
    // NOTE: using IDENTITY (i.e. db does auto-increments, Hibernate reads the result back)
    // can be a performance problem if there is a need to do bulk inserts.
    // Using SEQUENCE would allow getting multiple ids more efficiently.
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) override var id: Int = 0,

    // related
    // orphanRemoval is needed for even explicit child-object removal. JPA weirdness...
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hanke",
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    var listOfHankeYhteystieto: MutableList<HankeYhteystietoEntity> = mutableListOf(),
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hanke",
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    var alueet: MutableList<HankealueEntity> = mutableListOf(),
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hanke",
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    var liitteet: MutableList<HankeAttachmentEntity> = mutableListOf(),
) : HankeIdentifier {
    // --------------- Hankkeen lisätiedot / Työmaan tiedot -------------------
    var tyomaaKatuosoite: String? = null

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hanketyomaatyyppi", joinColumns = [JoinColumn(name = "hankeid")])
    @Enumerated(EnumType.STRING)
    var tyomaaTyyppi: MutableSet<TyomaaTyyppi> = mutableSetOf()

    // --------------- Hankkeen haitat -------------------
    // These five fields have generic string values, so can just as well store them with the ordinal
    // number.
    var kaistaHaitta: TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin? = null
    var kaistaPituusHaitta: KaistajarjestelynPituus? = null
    var meluHaitta: Haitta13? = null
    var polyHaitta: Haitta13? = null
    var tarinaHaitta: Haitta13? = null

    // Made bidirectional relation mainly to allow cascaded delete.
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hanke",
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    var tormaystarkasteluTulokset: MutableList<TormaystarkasteluTulosEntity> = mutableListOf()

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "hanke")
    var hakemukset: MutableSet<ApplicationEntity> = mutableSetOf()

    // ==================  Helper functions ================

    fun addYhteystieto(yhteystieto: HankeYhteystietoEntity) {
        // TODO: should check that the given entity is not yet connected to another Hanke, or
        // if already connected to this hanke (see addTormaystarkasteluTulos())
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

        if (status != other.status) return false
        if (hankeTunnus != other.hankeTunnus) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + hankeTunnus.hashCode()
        result = 31 * result + id
        return result
    }
}

interface HankeRepository : JpaRepository<HankeEntity, Int> {
    fun findOneByHankeTunnus(hankeTunnus: String): HankeIdentifier?

    fun findByHankeTunnus(hankeTunnus: String): HankeEntity?

    override fun findAll(): List<HankeEntity>

    fun findAllByStatus(status: HankeStatus): List<HankeEntity>
}

interface HankeIdentifier : HasId<Int> {
    override val id: Int
    val hankeTunnus: String

    fun logString() = "Hanke: (id=${id}, tunnus=${hankeTunnus})"
}

enum class CounterType {
    HANKETUNNUS
}

@Entity
@Table(name = "idcounter")
class IdCounter(
    @Id @Enumerated(EnumType.STRING) var counterType: CounterType? = null,
    var value: Long? = null
)

interface IdCounterRepository : JpaRepository<IdCounter, CounterType> {
    /**
     * Basic principals:
     * - if current year is the same as before (in column 'year') return incrementing value
     * - if current year is not the same as before return 1
     *
     * This SQL clause has some PostgreSQL specific thingies:
     * - 'WITH' clause describes a 'variable' table used inside query in two places
     * - 'FOR UPDATE' in nested SELECT clause makes sure that no other process can update the row
     *   during this whole UPDATE clause
     * - 'RETURNING' in the end is for UPDATE clase to return not just the number of affected rows
     *   but also the column data of those rows (a single row in our case)
     *
     * With these specialities we can assure that concurrent calls for this method will never return
     * duplicate values.
     *
     * Notice also that the method returns a list even though there is always only max. 1 item in it
     * because counterType is PK.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
            WITH currentyear AS (SELECT EXTRACT(YEAR FROM now() AT TIME ZONE 'UTC') AS date_part)
            UPDATE 
                idcounter
            SET
                value = CASE
                    WHEN year = currentyear.date_part THEN 
                        (SELECT value FROM IdCounter WHERE counterType = :counterType FOR UPDATE) + 1
                    ELSE 
                        1 
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

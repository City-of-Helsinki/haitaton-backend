package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.domain.HankeReminder
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.domain.Hankevaihe
import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.domain.Loggable
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

// Build-time plugins will open the class and add no-arg constructor for @Entity classes.

@Entity
@Table(name = "hanke")
class HankeEntity(
    @Enumerated(EnumType.STRING) var status: HankeStatus = HankeStatus.DRAFT,
    override val hankeTunnus: String,
    var nimi: String,
    var kuvaus: String? = null,
    @Enumerated(EnumType.STRING) var vaihe: Hankevaihe? = null,
    var onYKTHanke: Boolean? = false,
    var version: Int? = 0,
    // NOTE: creatorUserId must be non-null for valid data, but to allow creating instances with
    // no-arg constructor and programming convenience, this class allows it to be null
    // (temporarily).
    var createdByUserId: String? = null,
    var createdAt: LocalDateTime? = null,
    var modifiedByUserId: String? = null,
    var modifiedAt: LocalDateTime? = null,
    var completedAt: OffsetDateTime? = null,
    var generated: Boolean = false,
    // NOTE: using IDENTITY (i.e. db does auto-increments, Hibernate reads the result back)
    // can be a performance problem if there is a need to do bulk inserts.
    // Using SEQUENCE would allow getting multiple ids more efficiently.
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) override var id: Int = 0,
    @Column(name = "sent_reminders", nullable = false)
    @Enumerated(EnumType.STRING)
    @Suppress("JpaAttributeTypeInspection")
    var sentReminders: Array<HankeReminder> = arrayOf(),

    // related
    // orphanRemoval is needed for even explicit child-object removal. JPA weirdness...
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hanke",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var yhteystiedot: MutableList<HankeYhteystietoEntity> = mutableListOf(),
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hanke",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var alueet: MutableList<HankealueEntity> = mutableListOf(),
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hanke",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var liitteet: MutableList<HankeAttachmentEntity> = mutableListOf(),
) : HankeIdentifier {
    // --------------- Hankkeen lisätiedot / Työmaan tiedot -------------------
    var tyomaaKatuosoite: String? = null

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hanketyomaatyyppi", joinColumns = [JoinColumn(name = "hankeid")])
    @Enumerated(EnumType.STRING)
    var tyomaaTyyppi: MutableSet<TyomaaTyyppi> = mutableSetOf()

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "hanke")
    var hakemukset: MutableSet<HakemusEntity> = mutableSetOf()

    // ==================  Helper functions ================

    fun removeYhteystieto(yhteystieto: HankeYhteystietoEntity) {
        yhteystieto.id?.let { id ->
            yhteystiedot.removeAll { it.id == id }
            yhteystieto.hanke = null
        }
    }

    fun endDate(): LocalDate? = alueet.mapNotNull { it.haittaLoppuPvm }.maxOrNull()

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
    fun findOneById(id: Int): HankeIdentifier?

    fun findOneByHankeTunnus(hankeTunnus: String): HankeIdentifier?

    fun findByHankeTunnus(hankeTunnus: String): HankeEntity?

    fun findAllByStatus(status: HankeStatus): List<HankeEntity>

    @Query(
        "select h.id " +
            "from HankeEntity h " +
            "left join HankealueEntity ha on ha.hanke = h " +
            "where h.status = 'PUBLIC' " +
            "group by h.id " +
            "having coalesce(max(ha.haittaLoppuPvm), '1990-01-01') < CURRENT_DATE " +
            "order by h.modifiedAt asc limit :limit"
    )
    fun findHankeToComplete(limit: Int): List<Int>

    @Query(
        "select h.id from HankeEntity h " +
            "left join HankealueEntity ha on ha.hanke = h " +
            "where h.status = 'PUBLIC' " +
            "and not array_contains(h.sentReminders, :reminder) " +
            "group by h.id " +
            "having coalesce(max(ha.haittaLoppuPvm), '1990-01-01') <= :reminderDate " +
            "order by h.modifiedAt asc limit :limit"
    )
    fun findHankeToRemind(limit: Int, reminderDate: LocalDate, reminder: HankeReminder): List<Int>
}

interface HankeIdentifier : HasId<Int>, Loggable {
    override val id: Int
    val hankeTunnus: String

    override fun logString() = "Hanke: (id=${id}, tunnus=${hankeTunnus})"
}

enum class CounterType {
    HANKETUNNUS
}

@Entity
@Table(name = "idcounter")
class IdCounter(
    @Id @Enumerated(EnumType.STRING) var counterType: CounterType? = null,
    var value: Long? = null,
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
        nativeQuery = true,
    )
    fun incrementAndGet(counterType: String): List<IdCounter>
}

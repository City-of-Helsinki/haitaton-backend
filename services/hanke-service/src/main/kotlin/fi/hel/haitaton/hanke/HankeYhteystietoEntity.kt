package fi.hel.haitaton.hanke

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Yhteystieto
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.FetchType.EAGER
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository

enum class ContactType {
    OMISTAJA,
    RAKENNUTTAJA,
    TOTEUTTAJA,
    MUU,
}

@Entity
@Table(name = "hankeyhteystieto")
class HankeYhteystietoEntity(
    @JsonView(ChangeLogView::class) @Id @GeneratedValue(strategy = IDENTITY) var id: Int? = null,

    // must have contact information
    @JsonView(ChangeLogView::class) @Enumerated(STRING) var contactType: ContactType,
    @JsonView(ChangeLogView::class) var nimi: String,
    @JsonView(ChangeLogView::class) var email: String,

    // optional
    @JsonView(ChangeLogView::class) var puhelinnumero: String? = null,
    @JsonView(ChangeLogView::class) var organisaatioNimi: String? = null,
    @JsonView(ChangeLogView::class) var osasto: String? = null,
    @JsonView(ChangeLogView::class) var rooli: String? = null,

    /** For contacts with tyyppi other than YKSITYISHENKILO. */
    @JsonView(ChangeLogView::class) @Column(name = "y_tunnus") var ytunnus: String? = null,

    // Personal data processing restriction (or other needs to prevent changes)
    @JsonView(NotInChangeLogView::class) var dataLocked: Boolean? = false,
    @JsonView(NotInChangeLogView::class) var dataLockInfo: String? = null,

    // NOTE: createdByUserId must be non-null for valid data, but to allow creating instances with
    // no-arg constructor and programming convenience, this class allows it to be null
    // (temporarily).
    @JsonView(NotInChangeLogView::class) var createdByUserId: String? = null,
    @JsonView(NotInChangeLogView::class) var createdAt: LocalDateTime? = null,
    @JsonView(NotInChangeLogView::class) var modifiedByUserId: String? = null,
    @JsonView(NotInChangeLogView::class) var modifiedAt: LocalDateTime? = null,
    @JsonView(ChangeLogView::class) @Enumerated(STRING) var tyyppi: YhteystietoTyyppi? = null,
    @JsonView(NotInChangeLogView::class)
    @ManyToOne(fetch = EAGER)
    @JoinColumn(name = "hankeid")
    var hanke: HankeEntity? = null,
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hankeYhteystieto",
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    var yhteyshenkilot: MutableList<HankeYhteyshenkiloEntity> = mutableListOf(),
) {

    fun toDomain(): HankeYhteystieto =
        HankeYhteystieto(
            id = id,
            nimi = nimi,
            email = email,
            ytunnus = ytunnus,
            puhelinnumero = puhelinnumero,
            organisaatioNimi = organisaatioNimi,
            osasto = osasto,
            rooli = rooli,
            tyyppi = tyyppi,
            createdAt = createdAt?.zonedDateTime(),
            createdBy = createdByUserId,
            modifiedAt = modifiedAt?.zonedDateTime(),
            modifiedBy = modifiedByUserId,
            yhteyshenkilot = yhteyshenkilot.map { it.toDomain() },
        )

    // Must consider both id and all non-audit fields for correct operations in certain collections
    // Id can not be used as the only comparison, as one can have entities with null id (before they
    // get saved).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HankeYhteystietoEntity) return false

        if (id == other.id) return true

        if (contactType != other.contactType) return false
        if (nimi != other.nimi) return false
        if (email != other.email) return false
        if (puhelinnumero != other.puhelinnumero) return false
        if (organisaatioNimi != other.organisaatioNimi) return false
        if (osasto != other.osasto) return false
        if (yhteyshenkilot != other.yhteyshenkilot) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id ?: 0
        result = 31 * result + contactType.hashCode()
        result = 31 * result + nimi.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + puhelinnumero.hashCode()
        result = 31 * result + (organisaatioNimi?.hashCode() ?: 0)
        result = 31 * result + (osasto?.hashCode() ?: 0)
        result = 31 * result + yhteyshenkilot.hashCode()
        return result
    }

    /**
     * Returns a new instance with the main fields copied. Main fields being the contact type, name,
     * email, phone, organisation info.
     */
    fun cloneWithMainFields(): HankeYhteystietoEntity =
        HankeYhteystietoEntity(
            contactType = contactType,
            nimi = nimi,
            email = email,
            puhelinnumero = puhelinnumero,
            organisaatioNimi = organisaatioNimi,
            osasto = osasto,
            rooli = rooli,
            tyyppi = tyyppi,
        )

    companion object {
        fun fromDomain(
            hankeYht: Yhteystieto,
            contactType: ContactType,
            createdByUserId: String,
            hankeEntity: HankeEntity,
        ) =
            HankeYhteystietoEntity(
                contactType = contactType,
                nimi = hankeYht.nimi,
                email = hankeYht.email,
                ytunnus = hankeYht.ytunnus,
                puhelinnumero = hankeYht.puhelinnumero,
                organisaatioNimi = hankeYht.organisaatioNimi,
                osasto = hankeYht.osasto,
                rooli = hankeYht.rooli,
                tyyppi = hankeYht.tyyppi,
                dataLocked = false,
                dataLockInfo = null,
                createdByUserId = createdByUserId,
                createdAt = getCurrentTimeUTCAsLocalTime(),
                id = hankeYht.id,
                hanke = hankeEntity, // reference back to parent hanke
            )
    }
}

interface HankeYhteystietoRepository : JpaRepository<HankeYhteystietoEntity, Int> {}

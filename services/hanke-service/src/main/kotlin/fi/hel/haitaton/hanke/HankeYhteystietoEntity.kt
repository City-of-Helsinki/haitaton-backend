package fi.hel.haitaton.hanke

import com.fasterxml.jackson.annotation.JsonView
import java.time.LocalDateTime
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

enum class ContactType {
    OMISTAJA, // owner
    ARVIOIJA, // planner or person to do the planning of hanke
    TOTEUTTAJA // implementor or builder
}

@Entity
@Table(name = "hankeyhteystieto")
class HankeYhteystietoEntity(
    @JsonView(ChangeLogView::class) @Enumerated(EnumType.STRING) var contactType: ContactType,

    // must have contact information
    @JsonView(ChangeLogView::class) var sukunimi: String,
    @JsonView(ChangeLogView::class) var etunimi: String,
    @JsonView(ChangeLogView::class) var email: String,
    @JsonView(ChangeLogView::class) var puhelinnumero: String,
    @JsonView(ChangeLogView::class) var organisaatioId: Int? = 0,
    @JsonView(ChangeLogView::class) var organisaatioNimi: String? = null,
    @JsonView(ChangeLogView::class) var osasto: String? = null,

    // Personal data processing restriction (or other needs to prevent changes)
    var dataLocked: Boolean? = false,
    var dataLockInfo: String? = null,

    // NOTE: createdByUserId must be non-null for valid data, but to allow creating instances with
    // no-arg constructor and programming convenience, this class allows it to be null
    // (temporarily).
    var createdByUserId: String? = null,
    var createdAt: LocalDateTime? = null,
    var modifiedByUserId: String? = null,
    var modifiedAt: LocalDateTime? = null,
    // NOTE: using IDENTITY (i.e. db does auto-increments, Hibernate reads the result back)
    // can be a performance problem if there is a need to do bulk inserts.
    // Using SEQUENCE would allow getting multiple ids more efficiently.
    @JsonView(ChangeLogView::class)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "hankeid") var hanke: HankeEntity? = null
) {

    // Must consider both id and all non-audit fields for correct operations in certain collections
    // Id can not be used as the only comparison, as one can have entities with null id (before they
    // get saved).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HankeYhteystietoEntity) return false

        if (id == other.id) return true

        if (contactType != other.contactType) return false
        if (sukunimi != other.sukunimi) return false
        if (etunimi != other.etunimi) return false
        if (email != other.email) return false
        if (puhelinnumero != other.puhelinnumero) return false
        if (organisaatioId != other.organisaatioId) return false
        if (organisaatioNimi != other.organisaatioNimi) return false
        if (osasto != other.osasto) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id ?: 0
        result = 31 * result + contactType.hashCode()
        result = 31 * result + sukunimi.hashCode()
        result = 31 * result + etunimi.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + puhelinnumero.hashCode()
        result = 31 * result + (organisaatioId ?: 0)
        result = 31 * result + (organisaatioNimi?.hashCode() ?: 0)
        result = 31 * result + (osasto?.hashCode() ?: 0)
        return result
    }

    /**
     * Returns a new instance with the main fields copied. Main fields being the contact type, name,
     * email, phone, organisation info.
     */
    fun cloneWithMainFields(): HankeYhteystietoEntity =
        HankeYhteystietoEntity(
            contactType,
            sukunimi,
            etunimi,
            email,
            puhelinnumero,
            organisaatioId,
            organisaatioNimi,
            osasto
        )
}

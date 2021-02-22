package fi.hel.haitaton.hanke

import java.time.LocalDateTime
import javax.persistence.*

enum class ContactType {
    OMISTAJA, //owner
    ARVIOIJA, //planner or person to do the planning of hanke
    TOTEUTTAJA // implementor or builder
}

@Entity
@Table(name = "hankeyhteystieto")
class HankeYhteystietoEntity (
        @Enumerated(EnumType.STRING)
        var contactType: ContactType,

        // must have contact information
        var sukunimi: String,
        var etunimi: String,
        var email: String,
        var puhelinnumero: String,

        var organisaatioId: Int? = 0,
        var organisaatioNimi: String? = null,
        var osasto: String? = null,

        // NOTE: createdByUserId must be non-null for valid data, but to allow creating instances with
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

        @ManyToOne(fetch = FetchType.EAGER)
        @JoinColumn(name="hankeid")
        var hanke: HankeEntity? = null
) {

    // Must consider both id and all non-audit fields for correct operations in certain collections
    // Id can not be used as the only comparison, as one can have entities with null id (before they get saved).
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
}

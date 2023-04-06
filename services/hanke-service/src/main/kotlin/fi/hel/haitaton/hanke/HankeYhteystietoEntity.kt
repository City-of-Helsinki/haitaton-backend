package fi.hel.haitaton.hanke

import com.fasterxml.jackson.annotation.JsonView
import com.vladmihalcea.hibernate.type.json.JsonType
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi
import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType.STRING
import javax.persistence.Enumerated
import javax.persistence.FetchType.EAGER
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType.IDENTITY
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef

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
    @JsonView(ChangeLogView::class) var organisaatioId: Int? = 0,
    @JsonView(ChangeLogView::class) var organisaatioNimi: String? = null,
    @JsonView(ChangeLogView::class) var osasto: String? = null,
    @JsonView(ChangeLogView::class) var rooli: String? = null,
    @Type(type = "json")
    @Column(columnDefinition = "jsonb")
    var alikontaktit: List<Alikontakti> = listOf(),

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
) {

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
        if (organisaatioId != other.organisaatioId) return false
        if (organisaatioNimi != other.organisaatioNimi) return false
        if (osasto != other.osasto) return false
        if (alikontaktit != other.alikontaktit) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id ?: 0
        result = 31 * result + contactType.hashCode()
        result = 31 * result + nimi.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + puhelinnumero.hashCode()
        result = 31 * result + (organisaatioId ?: 0)
        result = 31 * result + (organisaatioNimi?.hashCode() ?: 0)
        result = 31 * result + (osasto?.hashCode() ?: 0)
        result = 31 * result + alikontaktit.hashCode()
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
            organisaatioId = organisaatioId,
            organisaatioNimi = organisaatioNimi,
            osasto = osasto,
            rooli = rooli,
            tyyppi = tyyppi,
        )
}

@TypeDef(name = "json", typeClass = JsonType::class)
data class Alikontakti(
    val etunimi: String,
    val sukunimi: String,
    val email: String,
    val puhelinnumero: String,
)

package fi.hel.haitaton.hanke


import java.time.LocalDateTime
import javax.persistence.*


enum class ContactType {
    OMISTAJA, //owner
    ARVIOIJA, //planner or person to do the planning of hanke
    TOTEUTTAJA // implementator or builder
}


@Entity
@Table(name = "hankeyhteystieto")
class HankeYhteystietoEntity (
        @Enumerated(EnumType.STRING)
        var contactType: ContactType,
        var hankeId: Int = 0,

//must have contact information
        var sukunimi: String,
        var etunimi: String,
        var email: String,
        var puhelinnumero: String,

        var organisaatioid: Int? = 0,
        var organisaationimi: String? = null,
        var osasto: String? = null,

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

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name="hanke_id")
        private var user: HankeEntity? = null
)



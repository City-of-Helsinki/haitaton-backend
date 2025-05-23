package fi.hel.haitaton.hanke.permissions

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.ContactType
import fi.hel.haitaton.hanke.HankeYhteyshenkiloEntity
import fi.hel.haitaton.hanke.NotInChangeLogView
import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloEntity
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

data class HankekayttajaInput(
    val etunimi: String,
    val sukunimi: String,
    val email: String,
    val puhelin: String,
)

@Schema(description = "Api response of user data of given Hanke")
data class HankeKayttajaResponse(
    @field:Schema(description = "Hanke users") val kayttajat: List<HankeKayttajaDto>
)

@Schema(description = "Hanke user")
data class HankeKayttajaDto(
    @field:Schema(description = "Id, set by the service") val id: UUID,
    @field:Schema(description = "Email address") val sahkoposti: String,
    @field:Schema(description = "First name") val etunimi: String,
    @field:Schema(description = "Last name") val sukunimi: String,
    @field:Schema(description = "Phone number") val puhelinnumero: String,
    @field:Schema(description = "Access level in Hanke") val kayttooikeustaso: Kayttooikeustaso?,
    @field:Schema(description = "User roles in Hanke") val roolit: List<ContactType>,
    @field:Schema(description = "Has user logged in to view Hanke") val tunnistautunut: Boolean,
    @field:Schema(description = "When their latest invitation was sent")
    val kutsuttu: OffsetDateTime?,
)

@Entity
@Table(name = "hankekayttaja")
@JsonView(NotInChangeLogView::class)
class HankekayttajaEntity(
    @JsonView(ChangeLogView::class) @Id val id: UUID = UUID.randomUUID(),

    /** Related Hanke. */
    @Column(name = "hanke_id") val hankeId: Int,

    /** First name. */
    var etunimi: String,

    /** Last name. */
    var sukunimi: String,

    /** Phone number. */
    var puhelin: String,

    /** Email address. */
    var sahkoposti: String,

    /** Users first name used in a Hanke invitation. */
    @Column(name = "kutsuttu_etunimi") val kutsuttuEtunimi: String? = null,

    /** Users last name used in a Hanke invitation. */
    @Column(name = "kutsuttu_sukunimi") val kutsuttuSukunimi: String? = null,

    /** Related user permissions. */
    @OneToOne
    @JoinColumn(name = "permission_id", updatable = true, nullable = true)
    var permission: PermissionEntity?,

    /** Invitation data including e.g. the sign in token. */
    @OneToOne(mappedBy = "hankekayttaja", cascade = [CascadeType.ALL])
    var kayttajakutsu: KayttajakutsuEntity? = null,

    /** Identifier of the inviter. */
    @Column(name = "kutsuja_id") var kutsujaId: UUID? = null,
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hankeKayttaja",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var yhteyshenkilot: MutableList<HankeYhteyshenkiloEntity> = mutableListOf(),
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hankekayttaja",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var hakemusyhteyshenkilot: MutableList<HakemusyhteyshenkiloEntity> = mutableListOf(),
) {
    fun toDto(): HankeKayttajaDto =
        HankeKayttajaDto(
            id = id,
            sahkoposti = sahkoposti,
            etunimi = etunimi,
            sukunimi = sukunimi,
            puhelinnumero = puhelin,
            kayttooikeustaso = deriveKayttooikeustaso(),
            roolit = deriveRoolit(),
            tunnistautunut = permission != null,
            kutsuttu = kayttajakutsu?.createdAt,
        )

    fun toDomain(): HankeKayttaja =
        HankeKayttaja(
            id = id,
            hankeId = hankeId,
            etunimi = etunimi,
            sukunimi = sukunimi,
            sahkoposti = sahkoposti,
            puhelinnumero = puhelin,
            kayttooikeustaso = deriveKayttooikeustaso(),
            roolit = deriveRoolit(),
            permissionId = permission?.id,
            kayttajaTunnisteId = kayttajakutsu?.id,
            kutsuttu = kayttajakutsu?.createdAt,
        )

    /**
     * [KayttajakutsuEntity] stores kayttooikeustaso temporarily until user has signed in. After
     * that, [PermissionEntity] is used.
     *
     * Thus, kayttooikeustaso is read primarily from [PermissionEntity] if the relation exists.
     */
    fun deriveKayttooikeustaso(): Kayttooikeustaso? =
        permission?.kayttooikeustaso ?: kayttajakutsu?.kayttooikeustaso

    /**
     * Roles of the user are derived from the related [HankeYhteyshenkiloEntity]s. If there are no
     * related [HankeYhteyshenkiloEntity]s, the user has no roles. This means that the user is not a
     * contact person in any of the Hanke contacts.
     */
    private fun deriveRoolit(): List<ContactType> =
        yhteyshenkilot.map { it.hankeYhteystieto.contactType }

    fun fullName() = listOf(etunimi, sukunimi).filter { it.isNotBlank() }.joinToString(" ")
}

data class HankeKayttaja(
    override val id: UUID,
    val hankeId: Int,
    val etunimi: String,
    val sukunimi: String,
    val sahkoposti: String,
    val puhelinnumero: String,
    val kayttooikeustaso: Kayttooikeustaso?,
    val roolit: List<ContactType>,
    val permissionId: Int?,
    val kayttajaTunnisteId: UUID?,
    val kutsuttu: OffsetDateTime?,
) : HasId<UUID> {
    fun toDto(): HankeKayttajaDto =
        HankeKayttajaDto(
            id = id,
            sahkoposti = sahkoposti,
            etunimi = etunimi,
            sukunimi = sukunimi,
            puhelinnumero = puhelinnumero,
            kayttooikeustaso = kayttooikeustaso,
            roolit = roolit,
            tunnistautunut = permissionId != null,
            kutsuttu = kutsuttu,
        )
}

@Repository
interface HankekayttajaRepository : JpaRepository<HankekayttajaEntity, UUID> {
    fun findByHankeId(hankeId: Int): List<HankekayttajaEntity>

    fun findByHankeIdAndIdIn(hankeId: Int, ids: Collection<UUID>): List<HankekayttajaEntity>

    fun findByHankeIdAndSahkopostiIn(
        hankeId: Int,
        sahkopostit: List<String>,
    ): List<HankekayttajaEntity>

    fun findByPermissionId(permissionId: Int): HankekayttajaEntity?

    fun findByPermissionIdIn(permissionIds: Collection<Int>): List<HankekayttajaEntity>

    fun findByKutsujaId(kutsujaId: UUID): List<HankekayttajaEntity>
}

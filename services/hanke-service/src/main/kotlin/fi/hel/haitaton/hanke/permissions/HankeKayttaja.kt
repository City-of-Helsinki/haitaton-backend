package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeYhteyshenkiloEntity
import fi.hel.haitaton.hanke.domain.HasId
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
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

data class HankekayttajaInput(
    val etunimi: String,
    val sukunimi: String,
    val email: String,
    val puhelin: String,
) {
    fun fullName(): String = listOf(etunimi, sukunimi).filter { it.isNotBlank() }.joinToString(" ")
}

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
    @Deprecated("Use etunimi and sukunimi instead.")
    @field:Schema(description = "Full name", deprecated = true)
    val nimi: String,
    @field:Schema(description = "Phone number") val puhelinnumero: String,
    @field:Schema(description = "Access level in Hanke") val kayttooikeustaso: Kayttooikeustaso?,
    @field:Schema(description = "Has user logged in to view Hanke") val tunnistautunut: Boolean,
)

@Entity
@Table(name = "hankekayttaja")
class HankekayttajaEntity(
    @Id val id: UUID = UUID.randomUUID(),

    /** Related Hanke. */
    @Column(name = "hanke_id") val hankeId: Int,

    /** First name. */
    val etunimi: String,

    /** Last name. */
    val sukunimi: String,

    /** Phone number. */
    val puhelin: String,

    /** Email address. */
    val sahkoposti: String,

    /** Users first name used in a Hanke invitation. */
    @Column(name = "kutsuttu_etunimi") val kutsuttuEtunimi: String? = null,

    /** Users last name used in a Hanke invitation. */
    @Column(name = "kutsuttu_sukunimi") val kutsuttuSukunimi: String? = null,

    /** Related user permissions. */
    @OneToOne
    @JoinColumn(name = "permission_id", updatable = true, nullable = true)
    var permission: PermissionEntity?,

    /** Invitation data including e.g. the sign in token. */
    @OneToOne(mappedBy = "hankekayttaja") var kayttajakutsu: KayttajakutsuEntity? = null,

    /** Identifier of the inviter. */
    @Column(name = "kutsuja_id") val kutsujaId: UUID? = null,
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hankeKayttaja",
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    var yhteyshenkilot: MutableList<HankeYhteyshenkiloEntity> = mutableListOf(),
) {
    fun toDto(): HankeKayttajaDto =
        HankeKayttajaDto(
            id = id,
            sahkoposti = sahkoposti,
            etunimi = etunimi,
            sukunimi = sukunimi,
            nimi = fullName(),
            puhelinnumero = puhelin,
            kayttooikeustaso = deriveKayttooikeustaso(),
            tunnistautunut = permission != null,
        )

    fun toDomain(): HankeKayttaja =
        HankeKayttaja(
            id = id,
            hankeId = hankeId,
            nimi = fullName(),
            sahkoposti = sahkoposti,
            permissionId = permission?.id,
            kayttajaTunnisteId = kayttajakutsu?.id,
        )

    /**
     * [KayttajakutsuEntity] stores kayttooikeustaso temporarily until user has signed in. After
     * that, [PermissionEntity] is used.
     *
     * Thus, kayttooikeustaso is read primarily from [PermissionEntity] if the relation exists.
     */
    fun deriveKayttooikeustaso(): Kayttooikeustaso? =
        permission?.kayttooikeustaso ?: kayttajakutsu?.kayttooikeustaso

    fun fullName() = listOf(etunimi, sukunimi).filter { it.isNotBlank() }.joinToString(" ")
}

data class HankeKayttaja(
    override val id: UUID,
    val hankeId: Int,
    val nimi: String,
    val sahkoposti: String,
    val permissionId: Int?,
    val kayttajaTunnisteId: UUID?
) : HasId<UUID>

@Repository
interface HankekayttajaRepository : JpaRepository<HankekayttajaEntity, UUID> {
    fun findByHankeId(hankeId: Int): List<HankekayttajaEntity>

    fun findByHankeIdAndIdIn(hankeId: Int, ids: Collection<UUID>): List<HankekayttajaEntity>

    fun findByHankeIdAndSahkopostiIn(
        hankeId: Int,
        sahkopostit: List<String>
    ): List<HankekayttajaEntity>

    fun findByPermissionId(permissionId: Int): HankekayttajaEntity?
}

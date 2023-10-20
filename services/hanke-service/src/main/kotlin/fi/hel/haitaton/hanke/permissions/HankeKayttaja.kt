package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.domain.HasId
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Schema(description = "Api response of user data of given Hanke")
data class HankeKayttajaResponse(
    @field:Schema(description = "Hanke users") val kayttajat: List<HankeKayttajaDto>
)

@Schema(description = "Hanke user")
data class HankeKayttajaDto(
    @field:Schema(description = "Id, set by the service") val id: UUID,
    @field:Schema(description = "Email address") val sahkoposti: String,
    @field:Schema(description = "Full name") val nimi: String,
    @field:Schema(description = "Access level in Hanke") val kayttooikeustaso: Kayttooikeustaso?,
    @field:Schema(description = "Has user logged in to view Hanke") val tunnistautunut: Boolean,
)

@Entity
@Table(name = "hanke_kayttaja")
class HankeKayttajaEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "hanke_id") val hankeId: Int,
    val nimi: String,
    val sahkoposti: String,
    @OneToOne
    @JoinColumn(name = "permission_id", updatable = true, nullable = true)
    var permission: PermissionEntity?,
    @OneToOne(mappedBy = "hankeKayttaja") var kayttajaTunniste: KayttajaTunnisteEntity?,
) {
    fun toDto(): HankeKayttajaDto =
        HankeKayttajaDto(
            id = id,
            sahkoposti = sahkoposti,
            nimi = nimi,
            kayttooikeustaso = deriveKayttooikeustaso(),
            tunnistautunut = permission != null,
        )

    fun toDomain(): HankeKayttaja =
        HankeKayttaja(
            id = id,
            hankeId = hankeId,
            nimi = nimi,
            sahkoposti = sahkoposti,
            permissionId = permission?.id,
            kayttajaTunnisteId = kayttajaTunniste?.id,
        )

    /**
     * [KayttajaTunnisteEntity] stores kayttooikeustaso temporarily until user has signed in. After
     * that, [PermissionEntity] is used.
     *
     * Thus, kayttooikeustaso is read primarily from [PermissionEntity] if the relation exists.
     */
    fun deriveKayttooikeustaso(): Kayttooikeustaso? =
        permission?.kayttooikeustaso ?: kayttajaTunniste?.kayttooikeustaso
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
interface HankeKayttajaRepository : JpaRepository<HankeKayttajaEntity, UUID> {
    fun findByHankeId(hankeId: Int): List<HankeKayttajaEntity>

    fun findByHankeIdAndIdIn(hankeId: Int, ids: Collection<UUID>): List<HankeKayttajaEntity>

    fun findByHankeIdAndSahkopostiIn(
        hankeId: Int,
        sahkopostit: List<String>
    ): List<HankeKayttajaEntity>

    fun findByPermissionId(permissionId: Int): HankeKayttajaEntity?

    fun findByPermissionIdIn(permissionIds: Set<Int>): List<HankeKayttajaEntity>
}

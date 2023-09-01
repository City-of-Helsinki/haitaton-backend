package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.domain.Hanke
import org.springframework.stereotype.Service

@Service
class PermissionService(
    private val permissionRepository: PermissionRepository,
    private val kayttooikeustasoRepository: KayttooikeustasoRepository
) {
    fun findByHankeId(hankeId: Int) = permissionRepository.findAllByHankeId(hankeId)

    fun getAllowedHankeIds(userId: String, permission: PermissionCode): List<Int> =
        permissionRepository.findAllByUserIdAndPermission(userId, permission.code).map {
            it.hankeId
        }

    fun hasPermission(hankeId: Int, userId: String, permission: PermissionCode): Boolean {
        val kayttooikeustaso =
            permissionRepository.findOneByHankeIdAndUserId(hankeId, userId)?.kayttooikeustaso
        return hasPermission(kayttooikeustaso, permission)
    }

    fun setPermission(
        hankeId: Int,
        userId: String,
        kayttooikeustaso: Kayttooikeustaso
    ): PermissionEntity {
        val kayttooikeustasoEntity =
            kayttooikeustasoRepository.findOneByKayttooikeustaso(kayttooikeustaso)
        val entity =
            permissionRepository.findOneByHankeIdAndUserId(hankeId, userId)?.apply {
                this.kayttooikeustaso = kayttooikeustasoEntity
            }
                ?: PermissionEntity(
                    userId = userId,
                    hankeId = hankeId,
                    kayttooikeustaso = kayttooikeustasoEntity
                )
        return permissionRepository.save(entity)
    }

    fun verifyHankeUserAuthorization(userId: String, hanke: Hanke, permissionCode: PermissionCode) {
        val hankeId = hanke.id
        if (hankeId == null || !hasPermission(hankeId, userId, permissionCode)) {
            throw HankeNotFoundException(hanke.hankeTunnus)
        }
    }

    companion object {
        fun hasPermission(
            kayttooikeustaso: KayttooikeustasoEntity?,
            permission: PermissionCode
        ): Boolean = (kayttooikeustaso?.permissionCode ?: 0) and permission.code > 0
    }
}

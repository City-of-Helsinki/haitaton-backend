package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.logging.PermissionLoggingService
import java.util.EnumMap
import java.util.EnumSet
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class PermissionService(
    private val permissionRepository: PermissionRepository,
    private val kayttooikeustasoRepository: KayttooikeustasoRepository,
    private val logService: PermissionLoggingService,
) {
    private val permissionCache: EnumMap<Kayttooikeustaso, EnumSet<PermissionCode>> =
        EnumMap(Kayttooikeustaso::class.java)

    fun findByHankeId(hankeId: Int) = permissionRepository.findAllByHankeId(hankeId)

    fun getAllowedHankeIds(userId: String, permission: PermissionCode): List<Int> =
        permissionRepository.findAllByUserIdAndPermission(userId, permission.code).map {
            it.hankeId
        }

    @Transactional(readOnly = true)
    fun permissionsByHanke(userId: String): List<HankePermission> =
        permissionRepository.findHankePermissionsByUserId(userId)

    fun hasPermission(hankeId: Int, userId: String, permission: PermissionCode): Boolean =
        permissionRepository.findOneByHankeIdAndUserId(hankeId, userId)?.hasPermission(permission)
            ?: false

    fun hasPermission(kayttooikeustaso: Kayttooikeustaso, permission: PermissionCode): Boolean =
        permissionCache
            .getOrPut(kayttooikeustaso) {
                val codes =
                    kayttooikeustasoRepository
                        .findOneByKayttooikeustaso(kayttooikeustaso)
                        .permissionCodes
                EnumSet.noneOf(PermissionCode::class.java).apply { addAll(codes) }
            }
            .contains(permission)

    fun findPermission(hankeId: Int, userId: String): PermissionEntity? =
        permissionRepository.findOneByHankeIdAndUserId(hankeId, userId)

    @Transactional
    fun create(hankeId: Int, userId: String, kayttooikeustaso: Kayttooikeustaso): PermissionEntity {
        val kayttooikeustasoEntity = findKayttooikeustaso(kayttooikeustaso)
        val permission =
            permissionRepository.save(
                PermissionEntity(
                    userId = userId,
                    hankeId = hankeId,
                    kayttooikeustasoEntity = kayttooikeustasoEntity,
                ))
        logService.logCreate(permission.toDomain(), currentUserId())
        return permission
    }

    @Transactional
    fun updateKayttooikeustaso(
        permission: PermissionEntity,
        kayttooikeustaso: Kayttooikeustaso,
        currentUser: String
    ) {
        val kayttooikeustasoBefore = permission.kayttooikeustaso
        permission.kayttooikeustasoEntity = findKayttooikeustaso(kayttooikeustaso)
        permissionRepository.save(permission)
        logger.info {
            "Updated kayttooikeustaso for permission, " +
                "permissionId=${permission.id}, new kayttooikeustaso=$kayttooikeustaso, userId=$currentUser"
        }
        logService.logUpdate(kayttooikeustasoBefore, permission.toDomain(), currentUser)
    }

    private fun findKayttooikeustaso(kayttooikeustaso: Kayttooikeustaso): KayttooikeustasoEntity =
        kayttooikeustasoRepository.findOneByKayttooikeustaso(kayttooikeustaso)
}

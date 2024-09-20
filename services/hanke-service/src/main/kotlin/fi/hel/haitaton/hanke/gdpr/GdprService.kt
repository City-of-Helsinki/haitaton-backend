package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionEntity
import fi.hel.haitaton.hanke.permissions.PermissionRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class GdprService(
    private val hankeService: HankeService,
    private val permissionRepository: PermissionRepository,
    private val hankekayttajaRepository: HankekayttajaRepository,
    private val hankeRepository: HankeRepository,
) {
    @Transactional(readOnly = true)
    fun findGdprInfo(userId: String): CollectionNode? {
        val kayttajat = findKayttajat(userId)

        val hankeyhteystiedot =
            kayttajat
                .flatMap { it.yhteyshenkilot }
                .map { it.hankeYhteystieto }
                .filter { it.tyyppi != YhteystietoTyyppi.YKSITYISHENKILO }
        val hakemusyhteystiedot =
            kayttajat
                .flatMap { it.hakemusyhteyshenkilot }
                .map { it.hakemusyhteystieto }
                .filter { it.tyyppi != CustomerType.PERSON }

        return GdprJsonConverter.createGdprJson(
            kayttajat.map { it.toDomain() },
            hankeyhteystiedot.map { it.toDomain() },
            hakemusyhteystiedot.map { it.toDomain() },
            userId,
        )
    }

    @Transactional(readOnly = true)
    fun canDelete(userId: String): Boolean {
        logger.info { "Checking if we can delete information for user $userId" }

        val errors =
            findKayttajat(userId)
                .flatMap { kayttaja ->
                    activeApplicationErrors(kayttaja) +
                        onlyAdminInHankeWithActiveApplications(kayttaja)
                }
                .toSet()

        if (errors.isNotEmpty()) {
            throw DeleteForbiddenException(errors.toList())
        }
        return true
    }

    @Transactional
    fun deleteInfo(userId: String) {
        logger.info { "Deleting all information have on user $userId" }
        findKayttajat(userId).forEach { kayttaja -> tryToDeleteKayttaja(kayttaja) }
    }

    private fun findKayttajat(userId: String): List<HankekayttajaEntity> {
        val permissionsIds = permissionRepository.findAllByUserId(userId).map { it.id }
        return hankekayttajaRepository.findByPermissionIdIn(permissionsIds)
    }

    /**
     * Returns GdprErrors for the applications of the hanke the kayttaja belongs to, if the kayttaja
     * is the only admin (user with KAIKKI_OIKEUDET privileges) in the hanke.
     */
    private fun onlyAdminInHankeWithActiveApplications(
        kayttaja: HankekayttajaEntity
    ): List<GdprError> {
        if (!isAdmin(kayttaja) || hankeHasOtherAdmins(kayttaja)) {
            return listOf()
        }

        return hankeApplicationErrors(kayttaja)
    }

    /**
     * Returns GdprErrors if there are active applications on the hanke the kayttaja belongs to,
     * whether the kayttaja is a contact in those applications or not.
     */
    private fun hankeApplicationErrors(kayttaja: HankekayttajaEntity): List<GdprError> {
        val activeApplications =
            hankeRepository.getReferenceById(kayttaja.hankeId).hakemukset.filter {
                it.alluid != null
            }
        return activeApplications.map { GdprError.fromSentApplication(it.applicationIdentifier) }
    }

    private fun hankeHasOtherAdmins(kayttaja: HankekayttajaEntity): Boolean =
        otherUsers(kayttaja).any(this::isAdmin)

    private fun hankeHasOtherUsers(kayttaja: HankekayttajaEntity): Boolean =
        otherUsers(kayttaja).isNotEmpty()

    private fun otherUsers(kayttaja: HankekayttajaEntity): List<PermissionEntity> {
        val hankePermissions = permissionRepository.findAllByHankeId(kayttaja.hankeId)
        return hankePermissions.filter { it.userId != kayttaja.permission!!.userId }
    }

    private fun isAdmin(kayttaja: HankekayttajaEntity): Boolean = isAdmin(kayttaja.permission!!)

    private fun isAdmin(permission: PermissionEntity): Boolean =
        permission.kayttooikeustaso == Kayttooikeustaso.KAIKKI_OIKEUDET

    private fun activeApplicationErrors(kayttaja: HankekayttajaEntity): List<GdprError> {
        val activeApplications =
            kayttaja.hakemusyhteyshenkilot
                .map { it.hakemusyhteystieto }
                .map { it.application }
                .filter { it.alluid != null }

        return activeApplications.map { GdprError.fromSentApplication(it.applicationIdentifier) }
    }

    private fun tryToDeleteKayttaja(kayttaja: HankekayttajaEntity) {
        // Re-check application errors inside the delete transaction.
        // Collecting all errors is not important, even having one at this point is very unlikely.
        val activeApplicationErrors = activeApplicationErrors(kayttaja)
        if (activeApplicationErrors.isNotEmpty()) {
            logger.info {
                "The user is a contact on an active application. Refusing to delete their information. hankeId=${kayttaja.hankeId}"
            }
            throw DeleteForbiddenException(activeApplicationErrors)
        }

        if (!isAdmin(kayttaja)) {
            logger.info {
                "The user is not an admin, so we can delete the kayttaja. hankeId=${kayttaja.hankeId}"
            }
            deleteKayttaja(kayttaja)
            return
        }

        if (hankeHasOtherAdmins(kayttaja)) {
            logger.info {
                "The user is an admin, but there will be an admin remaining on the hanke, " +
                    "so we can delete the kayttaja. hankeId=${kayttaja.hankeId}"
            }
            deleteKayttaja(kayttaja)
            return
        }

        val hankeApplicationErrors = hankeApplicationErrors(kayttaja)
        if (hankeApplicationErrors.isNotEmpty()) {
            logger.info {
                "The user was the only user with KAIKKI_OIKEUDET privileges in the hanke, " +
                    "which also had at least one active application. Refusing to delete their " +
                    "information. hankeId=${kayttaja.hankeId}"
            }
            throw DeleteForbiddenException(hankeApplicationErrors)
        }

        if (hankeHasOtherUsers(kayttaja)) {
            // There are other users, so don't delete the whole hanke.
            // This will leave the hanke without any users with KAIKKI_OIKEUDET, so the hanke cannot
            // be fully managed by the remaining users. Hopefully, they'll contact support so
            // someone can restore full access manually.
            // Logging this as an error so someone might proactively contact someone in the hanke.
            logger.error {
                "Deleting the last remaining ${Kayttooikeustaso.KAIKKI_OIKEUDET} user from the " +
                    "hanke. Manual action will be needed to restore full access to the hanke. " +
                    "hankeId=${kayttaja.hankeId}"
            }
            deleteKayttaja(kayttaja)
        } else {
            // There are no other users, so no one will be able to see the hanke, so remove it
            // completely.
            deleteHanke(kayttaja)
        }
    }

    private fun deleteHanke(kayttaja: HankekayttajaEntity) {
        val hanke = hankeRepository.findOneById(kayttaja.hankeId)!!
        logger.info {
            "There are no other users for this hanke, so removing the hanke in response to the GDPR request. ${hanke.logString()}"
        }

        // Not really sure why the user delete needs to be flushed before deleting the hanke, but
        // not doing so causes a constraint violation from hakemusyhteystieto to
        // hakemusyhteyshenkilo.
        hankekayttajaRepository.findByKutsujaId(kayttaja.id).forEach { it.kutsujaId = null }
        hankekayttajaRepository.delete(kayttaja)
        hankekayttajaRepository.flush()

        hankeService.deleteHanke(hanke.hankeTunnus, kayttaja.permission!!.userId)
    }

    private fun deleteKayttaja(kayttaja: HankekayttajaEntity) {
        hankekayttajaRepository.findByKutsujaId(kayttaja.id).forEach { it.kutsujaId = null }
        hankekayttajaRepository.delete(kayttaja)
    }
}

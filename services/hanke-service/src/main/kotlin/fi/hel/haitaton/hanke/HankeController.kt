package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.logging.YhteystietoLoggingService
import fi.hel.haitaton.hanke.permissions.Permission
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionProfiles
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.validation.ValidHanke
import javax.validation.ConstraintViolationException
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/hankkeet")
@Validated
class HankeController(
    @Autowired private val hankeService: HankeService,
    @Autowired private val hankeGeometriatService: HankeGeometriatService,
    @Autowired private val permissionService: PermissionService,
    @Autowired private val yhteystietoLoggingService: YhteystietoLoggingService,
) {

    @GetMapping("/{hankeTunnus}")
    fun getHankeByTunnus(@PathVariable(name = "hankeTunnus") hankeTunnus: String): Hanke {
        val hanke = hankeService.loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        val userid = SecurityContextHolder.getContext().authentication.name
        val permission = permissionService.getPermissionByHankeIdAndUserId(hanke.id!!, userid)
        if (permission == null || !permission.permissions.contains(PermissionCode.VIEW)) {
            throw HankeNotFoundException(hankeTunnus)
        }

        yhteystietoLoggingService.saveDisclosureLogForUser(hanke, userid)
        return hanke
    }

    @GetMapping
    fun getHankeList(@RequestParam geometry: Boolean = false): List<Hanke> {
        val userid = SecurityContextHolder.getContext().authentication.name

        val userPermissions =
            permissionService.getPermissionsByUserId(userid).filter {
                it.permissions.contains(PermissionCode.VIEW)
            }

        val hankeList = hankeService.loadHankkeetByIds(userPermissions.map { it.hankeId })
        includePermissions(hankeList, userPermissions)

        if (geometry) {
            hankeList.forEach { it.geometriat = hankeGeometriatService.loadGeometriat(it) }
        }

        yhteystietoLoggingService.saveDisclosureLogsForUser(hankeList, userid)
        return hankeList
    }

    private fun includePermissions(hankeList: List<Hanke>, userPermissions: List<Permission>) {
        val permissionsByHankeId = userPermissions.associateBy { it.hankeId }
        hankeList.forEach { it.permissions = permissionsByHankeId.get(it.id!!)?.permissions }
    }

    /** Add one hanke. This method will be called when we do not have id for hanke yet */
    @PostMapping
    fun createHanke(@ValidHanke @RequestBody hanke: Hanke?): Hanke {
        logger.info { "Creating Hanke: ${hanke?.toLogString()}" }

        if (hanke == null) {
            throw HankeArgumentException("No hanke given when creating hanke")
        }
        val createdHanke = hankeService.createHanke(hanke)

        permissionService.setPermission(
            createdHanke.id!!,
            createdHanke.createdBy!!,
            PermissionProfiles.HANKE_OWNER_PERMISSIONS
        )
        yhteystietoLoggingService.saveDisclosureLogForUser(createdHanke, createdHanke.createdBy)
        return createdHanke
    }

    /** Update one hanke. */
    @PutMapping("/{hankeTunnus}")
    fun updateHanke(
        @ValidHanke @RequestBody hanke: Hanke?,
        @PathVariable hankeTunnus: String?
    ): Hanke {
        logger.info { "Updating Hanke: ${hanke?.toLogString()}" }
        if (hanke == null) {
            throw HankeArgumentException("No hanke given when updating hanke")
        }
        if (hankeTunnus == null || hankeTunnus != hanke.hankeTunnus) {
            throw HankeArgumentException("Hanketunnus not given or doesn't match the hanke data")
        }
        hanke.geometriat = hankeGeometriatService.loadGeometriat(hanke)
        val updatedHanke = hankeService.updateHanke(hanke)
        logger.info { "Updated hanke ${updatedHanke.hankeTunnus}." }
        yhteystietoLoggingService.saveDisclosureLogForUser(updatedHanke, updatedHanke.modifiedBy!!)
        return updatedHanke
    }

    @DeleteMapping("/{hankeTunnus}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteHanke(@PathVariable hankeTunnus: String) {
        logger.info { "Deleting hanke: $hankeTunnus" }

        val hanke = hankeService.loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)
        val hankeId = hanke.id!!

        val userid = SecurityContextHolder.getContext().authentication.name
        val permissions = permissionService.getPermissionByHankeIdAndUserId(hankeId, userid)
        if (permissions == null || !permissions.permissions.contains(PermissionCode.DELETE)) {
            throw HankeNotFoundException(hankeTunnus)
        }

        hankeService.deleteHanke(hankeId)

        logger.info { "Deleted Hanke: ${hanke.toLogString()}" }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HankeArgumentException::class)
    fun handleArgumentExceptions(ex: HankeArgumentException): HankeError {
        logger.warn { ex.message }
        return HankeError.HAI1002
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleValidationExceptions(ex: ConstraintViolationException): HankeError {
        logger.warn { ex.message }
        return ex.toHankeError(HankeError.HAI1002)
    }
}

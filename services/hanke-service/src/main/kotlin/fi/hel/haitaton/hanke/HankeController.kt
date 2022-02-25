package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
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

private val logger = KotlinLogging.logger { }

@RestController
@RequestMapping("/hankkeet")
@Validated
class HankeController(
    @Autowired private val hankeService: HankeService,
    @Autowired private val hankeGeometriatService: HankeGeometriatService,
    @Autowired private val permissionService: PermissionService
) {

    @GetMapping("/{hankeTunnus}")
    fun getHankeByTunnus(
            @PathVariable(name = "hankeTunnus") hankeTunnus: String,
            @RequestParam(name = "geometry", required = false, defaultValue = "true") geometry: Boolean = true)
    : Hanke {
        val hanke = hankeService.loadHanke(hankeTunnus)
                ?: throw HankeNotFoundException(hankeTunnus)

        val userid = SecurityContextHolder.getContext().authentication.name
        val permission = permissionService.getPermissionByHankeIdAndUserId(hanke.id!!, userid)
        if (permission == null || !permission.permissions.contains(PermissionCode.VIEW)) {
            throw HankeNotFoundException(hankeTunnus)
        }

        if (geometry) {
            hanke.geometriat = hankeGeometriatService.loadGeometriat(hanke)
        }

        return hanke
    }

    @GetMapping
    fun getHankeList(@RequestParam geometry: Boolean = false): List<Hanke> {
        val userid = SecurityContextHolder.getContext().authentication.name

        val userPermissions = permissionService.getPermissionsByUserId(userid)
                .filter { it.permissions.contains(PermissionCode.VIEW) }

        val hankeList = hankeService.loadHankkeetByIds(userPermissions.map { it.hankeId })
        includePermissions(hankeList, userPermissions)

        if (geometry) {
            hankeList.forEach { it.geometriat = hankeGeometriatService.loadGeometriat(it) }
        }

        return hankeList
    }

    private fun includePermissions(hankeList: List<Hanke>, userPermissions: List<Permission>) {
        val permissionsByHankeId = userPermissions.associateBy { it.hankeId }
        hankeList.forEach { it.permissions = permissionsByHankeId.get(it.id!!)?.permissions }
    }

    /**
     * Add one hanke.
     * This method will be called when we do not have id for hanke yet
     */
    @PostMapping
    fun createHanke(@ValidHanke @RequestBody hanke: Hanke): Hanke {
        logger.debug { "Creating Hanke: ${hanke.toLogString()}" }

        val createdHanke = hankeService.createHanke(hanke)

        permissionService.setPermission(createdHanke.id!!, createdHanke.createdBy!!, PermissionProfiles.HANKE_OWNER_PERMISSIONS)
        return createdHanke
    }

    /**
     * Update one hanke.
     */
    @PutMapping("/{hankeTunnus}")
    fun updateHanke(@ValidHanke @RequestBody hanke: Hanke, @PathVariable hankeTunnus: String): Hanke {
        logger.debug { "Updating Hanke: ${hanke.toLogString()}" }

        if (hankeTunnus != hanke.hankeTunnus) {
            throw IllegalArgumentException("hankeTunnus must be equal between path and body value")
        }

        // Check user has EDIT permission
        val currentId = hankeService.loadHanke(hankeTunnus)?.id ?: throw HankeNotFoundException(hankeTunnus)
        val userid = SecurityContextHolder.getContext().authentication.name
        val permissions = permissionService.getPermissionByHankeIdAndUserId(currentId, userid)
        if (permissions == null || !permissions.permissions.contains(PermissionCode.EDIT)) {
            throw HankeNotFoundException(hankeTunnus)
        }

        hanke.geometriat = hankeGeometriatService.loadGeometriat(hanke)
        val updatedHanke = hankeService.updateHanke(hanke)

        logger.debug { "Updated hanke ${updatedHanke.hankeTunnus}." }

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
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleValidationExceptions(ex: ConstraintViolationException): HankeError {
        logger.warn { ex.message }
        return ex.toHankeError(HankeError.HAI1002)
    }
}

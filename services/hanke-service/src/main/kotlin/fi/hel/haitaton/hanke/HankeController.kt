package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeSearch
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
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
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

    /**
     * Get one hanke with hankeTunnus.
     *
     * @return Hanke if found or 404 Not Found error if not found
     */
    @GetMapping("/{hankeTunnus}")
    fun getHankeByTunnus(@PathVariable(name = "hankeTunnus") hankeTunnus: String?): ResponseEntity<Any> {
        logger.info {
            "Getting Hanke $hankeTunnus..."
        }
        if (hankeTunnus == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)
        }
        val hanke = hankeService.loadHanke(hankeTunnus)
        logger.info {
            "Got Hanke $hankeTunnus: ${hanke?.toLogString()}"
        }
        return if (hanke == null) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError.HAI1001)
        } else {
            ResponseEntity.status(HttpStatus.OK).body(hanke)
        }
    }

    @GetMapping
    fun getHankeList(hankeSearch: HankeSearch? = null): ResponseEntity<Any> {
        val userid = SecurityContextHolder.getContext().authentication.name

        val hankeIdsWithViewPermissions = permissionService.getPermissionsByUserId(userid)
            .filter { it.permissions.contains(PermissionCode.VIEW) }
            .map { it.hankeId }

        val hankeList = hankeService.loadHankkeetByIds(hankeIdsWithViewPermissions)

        if (hankeSearch != null && hankeSearch.includeGeometry()) {
            includeGeometry(hankeList)
        }

        logger.debug {
            "Search results: ${hankeList.joinToString("\n") { it.toLogString() }}"
        }
        return ResponseEntity.status(HttpStatus.OK).body(hankeList)
    }

    private fun includeGeometry(hankeList: List<Hanke>) {
        hankeList.forEach { hanke ->
            hanke.geometriat = hankeGeometriatService.loadGeometriat(hanke)
        }
    }

    /**
     * Add one hanke.
     * This method will be called when we do not have id for hanke yet
     */
    @PostMapping
    fun createHanke(@ValidHanke @RequestBody hanke: Hanke?): ResponseEntity<Any> {
        logger.info {
            "Creating Hanke: ${hanke?.toLogString()}"
        }

        if (hanke == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)
        }
        val createdHanke = hankeService.createHanke(hanke)

        permissionService.setPermission(createdHanke.id!!, createdHanke.createdBy!!, PermissionProfiles.HANKE_OWNER_PERMISSIONS)
        return ResponseEntity.status(HttpStatus.OK).body(createdHanke)
    }

    /**
     * Update one hanke.
     */
    @PutMapping("/{hankeTunnus}")
    fun updateHanke(@ValidHanke @RequestBody hanke: Hanke?, @PathVariable hankeTunnus: String?): ResponseEntity<Any> {
        logger.info {
            "Updating Hanke: ${hanke?.toLogString()}"
        }
        if (hanke == null || hankeTunnus == null || hankeTunnus != hanke.hankeTunnus) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)
        }
        hanke.geometriat = hankeGeometriatService.loadGeometriat(hanke)
        val updatedHanke = hankeService.updateHanke(hanke)
        logger.info {
            "Updated hanke ${updatedHanke.hankeTunnus}."
        }
        return ResponseEntity.status(HttpStatus.OK).body(updatedHanke)
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleValidationExceptions(ex: ConstraintViolationException): HankeError {
        logger.warn { ex.message }
        return ex.toHankeError(HankeError.HAI1002)
    }
}

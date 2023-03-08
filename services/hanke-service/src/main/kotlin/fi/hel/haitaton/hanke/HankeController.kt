package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.permissions.Role
import fi.hel.haitaton.hanke.validation.ValidHanke
import io.swagger.v3.oas.annotations.Hidden
import javax.validation.ConstraintViolationException
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
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
    @Autowired private val permissionService: PermissionService,
    @Autowired private val disclosureLogService: DisclosureLogService,
) {

    @GetMapping("/{hankeTunnus}")
    fun getHankeByTunnus(@PathVariable(name = "hankeTunnus") hankeTunnus: String): Hanke {
        val hanke = hankeService.loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        val userid = currentUserId()
        if (!permissionService.hasPermission(hanke.id!!, userid, PermissionCode.VIEW)) {
            throw HankeNotFoundException(hankeTunnus)
        }

        disclosureLogService.saveDisclosureLogsForHanke(hanke, userid)
        return hanke
    }

    @GetMapping
    fun getHankeList(@RequestParam geometry: Boolean = false): List<Hanke> {
        val userid = currentUserId()

        val hankeIds = permissionService.getAllowedHankeIds(userid, PermissionCode.VIEW)
        val hankeList = hankeService.loadHankkeetByIds(hankeIds)

        if (!geometry) {
            hankeList.forEach { hanke -> hanke.alueet.forEach { alue -> alue.geometriat = null } }
        }

        disclosureLogService.saveDisclosureLogsForHankkeet(hankeList, userid)
        return hankeList
    }

    /** Add one hanke. This method will be called when we do not have id for hanke yet */
    @PostMapping
    fun createHanke(@ValidHanke @RequestBody hanke: Hanke?): Hanke {
        val userId = currentUserId()
        logger.info { "Creating Hanke for user $userId: ${hanke?.toLogString()} " }

        if (hanke == null) {
            throw HankeArgumentException("No hanke given when creating hanke")
        }
        val createdHanke = hankeService.createHanke(hanke)

        permissionService.setPermission(
            createdHanke.id!!,
            currentUserId(),
            Role.KAIKKI_OIKEUDET,
        )
        disclosureLogService.saveDisclosureLogsForHanke(createdHanke, userId)
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
        val hankeId = hankeService.getHankeId(hankeTunnus)
        if (
            hankeId == null ||
                !permissionService.hasPermission(hankeId, currentUserId(), PermissionCode.EDIT)
        ) {
            throw HankeNotFoundException(hankeTunnus)
        }
        val updatedHanke = hankeService.updateHanke(hanke)
        logger.info { "Updated hanke ${updatedHanke.hankeTunnus}." }
        disclosureLogService.saveDisclosureLogsForHanke(updatedHanke, updatedHanke.modifiedBy!!)
        return updatedHanke
    }

    @DeleteMapping("/{hankeTunnus}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteHanke(@PathVariable hankeTunnus: String) {
        logger.info { "Deleting hanke: $hankeTunnus" }

        val hankeId =
            hankeService.getHankeId(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        val userId = currentUserId()
        if (!permissionService.hasPermission(hankeId, userId, PermissionCode.DELETE)) {
            throw HankeNotFoundException(hankeTunnus)
        }

        hankeService.deleteHanke(hankeId, userId).let { result ->
            logger.info { "Deletion of Hanke: $hankeTunnus successful: $result" }
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HankeArgumentException::class)
    @Hidden
    fun handleArgumentExceptions(ex: HankeArgumentException): HankeError {
        logger.warn { ex.message }
        return HankeError.HAI1002
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException::class)
    @Hidden
    fun handleValidationExceptions(ex: ConstraintViolationException): HankeError {
        logger.warn { ex.message }
        return ex.toHankeError(HankeError.HAI1002)
    }
}

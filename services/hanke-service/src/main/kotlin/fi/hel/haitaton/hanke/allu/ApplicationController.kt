package fi.hel.haitaton.hanke.allu

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/hakemukset")
class ApplicationController(
    private val service: ApplicationService,
    private val disclosureLogService: DisclosureLogService
) {
    @GetMapping
    fun getAll(): List<Application> {
        val applications = service.getAllApplicationsForUser(currentUserId())
        disclosureLogService.saveDisclosureLogsForApplications(applications, currentUserId())
        return applications
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable(name = "id") id: Long): Application {
        val userId = currentUserId()
        val application = service.getApplicationById(id, userId)
        disclosureLogService.saveDisclosureLogsForApplication(application, userId)
        return application
    }

    @PostMapping
    fun create(@RequestBody application: Application): Application {
        val userId = currentUserId()
        val createdApplication = service.create(application, userId)
        disclosureLogService.saveDisclosureLogsForApplication(createdApplication, userId)
        return createdApplication
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable(name = "id") id: Long,
        @RequestBody application: Application
    ): Application {
        val userId = currentUserId()
        val updatedApplication =
            service.updateApplicationData(id, application.applicationData, userId)
        disclosureLogService.saveDisclosureLogsForApplication(updatedApplication, currentUserId())
        return updatedApplication
    }

    @PostMapping("/{id}/send-application")
    fun sendApplication(@PathVariable(name = "id") id: Long): Application =
        service.sendApplication(id, currentUserId())

    @ExceptionHandler(ApplicationNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun missingApplication(ex: ApplicationNotFoundException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2001
    }

    @ExceptionHandler(IncompatibleApplicationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun incompatibleApplicationData(ex: IncompatibleApplicationException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2002
    }
}

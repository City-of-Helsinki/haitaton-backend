package fi.hel.haitaton.hanke.allu

import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/hakemukset")
class ApplicationController(
    private val service: ApplicationService,
    private val disclosureLogService: DisclosureLogService
) {
    @GetMapping
    fun getAll(): List<ApplicationDto> {
        val applications = service.getAllApplicationsForCurrentUser()
        disclosureLogService.saveDisclosureLogsForApplications(applications, currentUserId())
        return applications
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable(name = "id") id: Long): ApplicationDto? {
        val application = service.getApplicationById(id)
        disclosureLogService.saveDisclosureLogsForApplication(application, currentUserId())
        return application
    }

    @PostMapping
    fun create(@RequestBody application: ApplicationDto): ApplicationDto {
        val userId = currentUserId()
        val createdApplication = service.create(application, userId)
        disclosureLogService.saveDisclosureLogsForApplication(createdApplication, userId)
        return createdApplication
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable(name = "id") id: Long,
        @RequestBody application: ApplicationDto
    ): ApplicationDto? {
        val userId = currentUserId()
        val updatedApplication =
            service.updateApplicationData(id, application.applicationData, userId)
        disclosureLogService.saveDisclosureLogsForApplication(updatedApplication, currentUserId())
        return updatedApplication
    }

    @PostMapping("/{id}/send-application")
    fun sendApplication(@PathVariable(name = "id") id: Long): ApplicationDto? =
        service.sendApplication(id)
}

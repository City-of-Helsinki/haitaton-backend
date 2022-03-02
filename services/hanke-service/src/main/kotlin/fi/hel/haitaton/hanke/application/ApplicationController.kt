package fi.hel.haitaton.hanke.application

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
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
    @Autowired private val applicationService: ApplicationService,
) {
    @PostMapping
    fun createApplication(@RequestBody application: ApplicationDTO): ApplicationDTO =
            applicationService.create(application);

    @GetMapping
    fun getApplications(): List<ApplicationDTO> =
            applicationService.getAllApplicationsForCurrentUser()

    @GetMapping("/{id}")
    fun getApplicationById(@PathVariable(name = "id") id : Long): ApplicationDTO? =
            applicationService.getApplicationById(id)

    @PutMapping("/{id}")
    fun updateApplication(
            @PathVariable(name = "id") id : Long,
            @RequestBody application: ApplicationDTO
    ): ApplicationDTO? = applicationService.updateApplicationData(id, application.applicationData)

    @PostMapping("/{id}/send-application")
    fun sendApplication(@PathVariable(name = "id") id : Long): ResponseEntity<Any> {
        val status = applicationService.sendApplication(id)
        return ResponseEntity.status(status).build()
    }
}
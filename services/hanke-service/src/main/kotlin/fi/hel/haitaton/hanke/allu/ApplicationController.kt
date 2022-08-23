package fi.hel.haitaton.hanke.allu

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/hakemukset")
class ApplicationController(@Autowired private val service: ApplicationService) {

    @GetMapping
    fun getAll() = service.getAllApplicationsForCurrentUser()

    @GetMapping("/{id}")
    fun getById(@PathVariable(name = "id") id: Long) = service.getApplicationById(id)

    @PostMapping
    fun create(@RequestBody application: ApplicationDTO) = service.create(application)

    @PutMapping("/{id}")
    fun update(
            @PathVariable(name = "id") id: Long,
            @RequestBody application: ApplicationDTO
    ): ApplicationDTO? = service.updateApplicationData(id, application.applicationData)

    @PostMapping("/{id}/send-application")
    fun sendApplication(@PathVariable(name = "id") id: Long) = service.sendApplication(id)

}

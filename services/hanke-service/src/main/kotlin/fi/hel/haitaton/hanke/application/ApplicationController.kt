package fi.hel.haitaton.hanke.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import io.sentry.protocol.App
import jdk.jshell.spi.ExecutionControl.NotImplementedException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
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
    fun createApplication(@RequestBody application: Application): ResponseEntity<Any> {
        val createdApplication = applicationService.create(application);
        return ResponseEntity.status(HttpStatus.OK).body(createdApplication);
    }

    @GetMapping
    fun getApplications() : List<Application> {
        return applicationService.getAllApplicationsForCurrentUser()
    }

    @GetMapping("/{id}")
    fun getApplicationById(@PathVariable(name = "id") id : Long) : ResponseEntity<Any> {
        val found = applicationService.getApplicationById(id)
        if(found.isEmpty)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        return ResponseEntity.status(HttpStatus.OK).body(found.get())
    }

    @PutMapping("/{id}")
    fun updateApplication(@PathVariable(name = "id") id : Long, @RequestBody application: Application) : ResponseEntity<Any> {
        val updated = applicationService.updateApplicationData(id, application.applicationData)
        if(!updated.isEmpty)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        return ResponseEntity.status(HttpStatus.OK).body(updated)
    }

    @PostMapping("/{id}/send-application")
    fun sendApplication(@PathVariable(name = "id") id : Long) : ResponseEntity<Any> {
        val found = applicationService.getApplicationById(id)
        if(found.isEmpty)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val application = found.get()

        //example, pitäskö olla allu clientissä?
        val mapper = ObjectMapper()
        when (application.applicationType) {
            ApplicationType.CABLE_REPORT -> {
                val alluApplication : CableReportApplication? = mapper.treeToValue(application.applicationData)
            }
        }
        throw NotImplementedException("Not implemented")
    }
}
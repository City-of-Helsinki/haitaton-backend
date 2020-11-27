package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.organisaatio.OrganisaatioService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*


/**
 * For testing Spring Boot Actuator endpoints
 */
@SpringBootTest(properties = [
    "management.server.port=",
    "spring.liquibase.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"])
@AutoConfigureMockMvc
@EnableAutoConfiguration
class ActuatorEndpointITests(@Autowired val mockMvc: MockMvc) {

    // Just to prevent the context trying to init that service, and fail doing it.
    @MockBean
    lateinit var hankeService: HankeService

    @MockBean
    lateinit var organisaatioService: OrganisaatioService

    @Test
    fun readiness() {
        mockMvc
                .perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk)
                .andExpect(content().contentType("application/vnd.spring-boot.actuator.v3+json"))
                .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun liveness() {
        mockMvc
                .perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk)
                .andExpect(content().contentType("application/vnd.spring-boot.actuator.v3+json"))
                .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun info() {
        mockMvc
                .perform(get("/actuator/info"))
                .andExpect(status().isOk)
                .andExpect(content().contentType("application/vnd.spring-boot.actuator.v3+json"))
                .andExpect(jsonPath("$.build.artifact").value("hanke-service"))
    }
}

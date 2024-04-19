package fi.hel.haitaton.hanke

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestTemplate

@SpringBootTest(
    properties = ["haitaton.api.disabled=true", "management.server.port="],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class ApiBlockingFilterAllowedEndpointsITests : IntegrationTest() {

    @LocalServerPort private var port: Int = 0

    private val restTemplate = RestTemplate()

    @ParameterizedTest
    @ValueSource(strings = ["/actuator/health/liveness", "/actuator/health/readiness", "/status"])
    fun `should return 200 'OK' for actuator paths`(path: String) {
        val response = restTemplate.getForEntity("http://localhost:$port$path", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }
}

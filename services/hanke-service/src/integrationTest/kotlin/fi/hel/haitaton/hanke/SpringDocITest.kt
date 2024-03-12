package fi.hel.haitaton.hanke

import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class SpringdocITest(@Autowired override val mockMvc: MockMvc) : ControllerTest, IntegrationTest() {

    @Test
    fun `Should display Swagger UI page`() {
        get("/swagger-ui/index.html", resultType = MediaType.TEXT_HTML)
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Swagger UI")))
    }

    @Test
    fun `Should load OpenAPI docs`() {
        get("/v3/api-docs")
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("API for Haitaton internal use.")))
    }
}

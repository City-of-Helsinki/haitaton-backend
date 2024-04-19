package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.test.JacksonTestExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header

@ExtendWith(JacksonTestExtension::class)
interface ControllerTest {
    val mockMvc: MockMvc

    /** Send a GET request to the given URL. */
    fun get(
        url: String,
        resultType: MediaType? = MediaType.APPLICATION_JSON,
    ): ResultActions {
        val actions =
            mockMvc.perform(MockMvcRequestBuilders.get(url).accept(MediaType.APPLICATION_JSON))
        return if (resultType != null) {
            actions.andExpect(content().contentType(resultType))
        } else {
            actions.andExpect(header().doesNotExist("content-type"))
        }
    }

    /**
     * Send a POST request with the given string as the request body as-is.
     *
     * Useful for testing malformed requests that can't be expressed with DTO objects.
     */
    fun postRaw(url: String, content: String): ResultActions {
        return mockMvc.perform(postRequest(url).content(content))
    }

    /** Send a POST request without a request body. */
    fun post(url: String): ResultActions = mockMvc.perform(postRequest(url))

    /** Send a POST request with the content object in the request body as JSON. */
    fun post(url: String, content: Any): ResultActions =
        mockMvc.perform(postRequest(url).body(content))

    /**
     * Send a PUT request with the given string as the request body as-is.
     *
     * Useful for testing malformed requests that can't be expressed with DTO objects.
     */
    fun putRaw(url: String, content: String): ResultActions {
        return mockMvc.perform(putRequest(url).content(content))
    }

    /** Send a POST request without a request body. */
    fun put(url: String): ResultActions = mockMvc.perform(putRequest(url))

    /** Send a POST request with the content object in the request body as JSON. */
    fun put(url: String, content: Any): ResultActions =
        mockMvc.perform(putRequest(url).body(content))

    /** Send a DELETE request without a request body. */
    fun delete(url: String): ResultActions = mockMvc.perform(deleteRequest(url))

    /** Use the given object as the request body for this request. Serialize the object as JSON. */
    private fun MockHttpServletRequestBuilder.body(content: Any): MockHttpServletRequestBuilder =
        this.content(OBJECT_MAPPER.writeValueAsString(content))

    private fun postRequest(url: String): MockHttpServletRequestBuilder =
        MockMvcRequestBuilders.post(url).modifyHeaders()

    private fun putRequest(url: String): MockHttpServletRequestBuilder =
        MockMvcRequestBuilders.put(url).modifyHeaders()

    private fun deleteRequest(url: String): MockHttpServletRequestBuilder =
        MockMvcRequestBuilders.delete(url).modifyHeaders()

    /**
     * Headers that are common for the HTTP methods that send a body with the request, like POST and
     * PUT.
     */
    private fun MockHttpServletRequestBuilder.modifyHeaders(): MockHttpServletRequestBuilder =
        this.accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding("UTF-8")
            .with(SecurityMockMvcRequestPostProcessors.csrf())
}

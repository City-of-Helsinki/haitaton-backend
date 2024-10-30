package fi.hel.haitaton.hanke

import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

/**
 * Check the result contains a specific HankeError.
 *
 * Example:
 * ```
 * post(BASE_URL, HankeFactory.create())
 *   .andExpect(status().isNotFound)
 *   .andExpect(hankeError(HankeError.HAI0004))
 * ```
 */
fun hankeError(error: HankeError) = ResultMatcher { result: MvcResult ->
    jsonPath("$.errorCode").value(error.getErrorCode()).match(result)
    jsonPath("$.errorMessage").value(error.errorMessage).match(result)
}

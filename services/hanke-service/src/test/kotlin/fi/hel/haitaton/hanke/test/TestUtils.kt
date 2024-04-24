package fi.hel.haitaton.hanke.test

import fi.hel.haitaton.hanke.getCurrentTimeUTC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

object TestUtils {
    const val mockedIp = "127.0.0.1"

    fun addMockedRequestIp(ip: String = mockedIp) {
        val request = MockHttpServletRequest()
        request.remoteAddr = ip
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    fun nextYear(): Int = getCurrentTimeUTC().year + 1
}

/**
 * Exception to use as a stand-in for communication errors etc. that can occur when calling the Allu
 * API. This needs to be a specific exception and not RuntimeException, because things like Mockk
 * errors are inherit RuntimeException, which can cause unexpected behaviour in tests.
 */
class AlluException : RuntimeException()

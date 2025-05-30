package fi.hel.haitaton.hanke.test

import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.getCurrentTimeUTC
import java.time.Clock
import java.time.Instant
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

object TestUtils {
    val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2025-04-09T01:41:13+03:00"), TZ_UTC)

    const val MOCKED_IP = "127.0.0.1"

    fun addMockedRequestIp(ip: String = MOCKED_IP) {
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

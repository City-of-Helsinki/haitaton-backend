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

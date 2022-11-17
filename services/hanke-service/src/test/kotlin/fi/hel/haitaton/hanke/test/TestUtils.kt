package fi.hel.haitaton.hanke.test

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

object TestUtils {
    fun addMockedRequestIp(ip: String = "127.0.0.1") {
        val request = MockHttpServletRequest()
        request.remoteAddr = ip
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }
}

package fi.hel.haitaton.hanke.security

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class LogoutService(
    @Qualifier("logoutJwtDecoder") private val jwtDecoder: JwtDecoder,
    private val userSessionRepository: UserSessionRepository,
) {
    @Transactional
    fun logout(token: String) {
        val jwt = jwtDecoder.decode(token)

        val (sid, sub) = parseSidAndSub(jwt)

        val deleted =
            if (sid != null) {
                userSessionRepository.deleteBySessionId(sid) > 0
            } else if (sub != null) {
                userSessionRepository.deleteBySubject(sub) > 0
            } else {
                throw JwtException("Logout token must contain 'sid' or 'sub' claim")
            }

        if (deleted) {
            logger.info { "Deleted user session" }
        } else {
            logger.info { "No user session found for logout token" }
        }
    }

    private fun parseSidAndSub(jwt: Jwt): Pair<String?, String?> {
        val sid = jwt.claims["sid"]?.toString()
        val sub = jwt.claims["sub"]?.toString()
        return Pair(sid, sub)
    }
}

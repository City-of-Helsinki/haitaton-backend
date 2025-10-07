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
        logger.info { "Decoding logout token..." }
        val jwt =
            try {
                jwtDecoder.decode(token)
            } catch (e: Exception) {
                logger.error(e) { "Failed to decode logout token: ${e.message}" }
                throw e
            }

        logger.info {
            "Successfully decoded logout token. Claims: iss=${jwt.issuer}, sub=${jwt.subject}, aud=${jwt.audience}"
        }

        val (sid, sub) = parseSidAndSub(jwt)
        logger.info { "Parsed logout token claims: sid=$sid, sub=$sub" }

        val terminated =
            if (sid != null) {
                val count = userSessionRepository.terminateBySessionId(sid)
                logger.info { "Terminated $count session(s) by sessionId=$sid" }
                count > 0
            } else if (sub != null) {
                val count = userSessionRepository.terminateBySubject(sub)
                logger.info { "Terminated $count session(s) by subject=$sub" }
                count > 0
            } else {
                throw JwtException("Logout token must contain 'sid' or 'sub' claim")
            }

        if (terminated) {
            logger.info { "Terminated user session: sid=$sid, sub=$sub" }
        } else {
            logger.info { "No user session found for logout token: sid=$sid, sub=$sub" }
        }
    }

    private fun parseSidAndSub(jwt: Jwt): Pair<String?, String?> {
        val sid = jwt.claims["sid"]?.toString()
        val sub = jwt.claims["sub"]?.toString()
        return Pair(sid, sub)
    }
}

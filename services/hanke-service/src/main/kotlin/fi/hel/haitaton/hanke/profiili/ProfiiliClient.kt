package fi.hel.haitaton.hanke.profiili

import org.springframework.stereotype.Component

/** Placeholder while waiting for actual implementation. */
@Component
class ProfiiliClient {
    fun getInfo(userId: String): UserInfo {
        throw NotImplementedError("Not implemented yet")
    }
}

data class UserInfo(
    val userId: String,
    val firstName: String,
    val lastName: String,
)

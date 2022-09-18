package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.profiili.UserInfo

object UserInfoFactory {
    fun teppoUserInfo(
        userId: String = "5296012a-117d-11ed-96cc-0a580a820245",
        firstName: String = "Teppo",
        lastName: String = "Testihenkilö",
    ) = UserInfo(userId, firstName, lastName)
}

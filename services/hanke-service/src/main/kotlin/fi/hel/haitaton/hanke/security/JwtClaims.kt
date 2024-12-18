package fi.hel.haitaton.hanke.security

/** Names of the claims we read from the JWT. */
object JwtClaims {
    const val AD_GROUPS = "ad_groups"
    const val AMR = "amr"
    const val GIVEN_NAME = "given_name"
    const val FAMILY_NAME = "family_name"
}

/** Values that the amr claim in the authentication JWT can have. */
object AmrValues {
    const val SUOMI_FI = "suomi_fi"
    const val AD = "helsinkiad"
}

package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.verifiedname.Names

object VerifiedNameFactory {

    // firstName and givenName are always identical now (see Names' KDoc), so the two default
    // constants below intentionally share the same value.
    const val DEFAULT_FIRST_NAME = "Antti"
    const val DEFAULT_LAST_NAME = "Apuhärmä"
    const val DEFAULT_GIVEN_NAME = "Antti"

    val DEFAULT_NAMES =
        Names(
            firstName = DEFAULT_FIRST_NAME,
            lastName = DEFAULT_LAST_NAME,
            givenName = DEFAULT_GIVEN_NAME
        )
}

package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.profiili.Names

object ProfiiliFactory {

    const val DEFAULT_FIRST_NAME = "Antti-Matti Tapani"
    const val DEFAULT_LAST_NAME = "Apuhärmä"
    const val DEFAULT_GIVEN_NAME = "Antti"

    val DEFAULT_NAMES =
        Names(
            firstName = DEFAULT_FIRST_NAME,
            lastName = DEFAULT_LAST_NAME,
            givenName = DEFAULT_GIVEN_NAME
        )
}

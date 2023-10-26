package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.permissions.UserContact

object UserContactFactory {

    val hakijaContact = UserContact("Henri Hakija", "henri.hakija@mail.com")

    val rakennuttajaContact = UserContact("Rane Rakennuttaja", "rane.rakennuttaja@mail.com")

    val asianhoitajaContact = UserContact("Anssi Asianhoitaja", "anssi.asianhoitaja@mail.com")

    val suorittajaContact = UserContact("Timo Työnsuorittaja", "timo.työnsuorittaja@mail.com")
}

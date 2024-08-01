package fi.hel.haitaton.hanke.pdf

import fi.hel.haitaton.hanke.hakemus.Hakemusyhteyshenkilo
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.PostalAddress
import java.time.ZoneId
import java.time.ZonedDateTime

fun PostalAddress.format(): String =
    "${this.streetAddress.streetName}\n${this.postalCode} ${this.city}"

fun Hakemusyhteyshenkilo.format(): String =
    listOfNotNull(kokoNimi(), sahkoposti, puhelin).filter { it.isNotBlank() }.joinToString("\n")

fun Hakemusyhteystieto.format(): String =
    listOfNotNull("$nimi\n", ytunnus, sahkoposti, puhelinnumero, "\nYhteyshenkilöt\n")
        .filter { it.isNotBlank() }
        .joinToString("\n") + this.yhteyshenkilot.joinToString("\n") { "\n" + it.format() }

fun ZonedDateTime?.format(): String? =
    this?.withZoneSameInstant(ZoneId.of("Europe/Helsinki"))?.format(finnishDateFormat)

fun Boolean?.format(): String = this?.let { if (it) "Kyllä" else "Ei" } ?: "-"

fun Float?.format(): String = "%.2f".format(this)

fun Double?.format(): String = "%.2f".format(this)

fun List<String>?.format(): String = if (this.isNullOrEmpty()) "-" else this.joinToString(", ")

fun String?.orDash() = if (this.isNullOrEmpty()) "-" else this

package fi.hel.haitaton.hanke.pdf

import fi.hel.haitaton.hanke.hakemus.Hakemusyhteyshenkilo
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.PostalAddress
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LOCALE = Locale("fi", "FI")
private val FINNISH_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.uuuu")

fun PostalAddress.format(): String =
    "${this.streetAddress.streetName}\n${this.postalCode} ${this.city}"

fun Hakemusyhteyshenkilo.format(): String =
    listOfNotNull(kokoNimi(), sahkoposti, puhelin).filter { it.isNotBlank() }.joinToString("\n")

fun Hakemusyhteystieto.format(): String =
    listOfNotNull("$nimi\n", ytunnus, sahkoposti, puhelinnumero, "\nYhteyshenkilöt\n")
        .filter { it.isNotBlank() }
        .joinToString("\n") + this.yhteyshenkilot.joinToString("\n") { "\n" + it.format() }

fun ZonedDateTime?.format(): String? =
    this?.withZoneSameInstant(ZoneId.of("Europe/Helsinki"))?.format(FINNISH_DATE_FORMAT)

fun Boolean?.format(): String = this?.let { if (it) "Kyllä" else "Ei" } ?: "-"

fun Float?.format(): String = "%.2f".format(LOCALE, this)

fun Double?.format(): String = "%.2f".format(LOCALE, this)

fun List<String>?.format(): String = if (this.isNullOrEmpty()) "-" else this.joinToString(", ")

fun String?.orDash() = if (this.isNullOrEmpty()) "-" else this

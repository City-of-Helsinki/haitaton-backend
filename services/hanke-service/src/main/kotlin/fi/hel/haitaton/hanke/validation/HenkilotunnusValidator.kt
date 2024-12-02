package fi.hel.haitaton.hanke.validation

import java.time.LocalDate
import java.time.format.DateTimeParseException
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

object HenkilotunnusValidator {

    private val hetuRegex =
        "^(\\d{2})(\\d{2})(\\d{2})([-+U-YA-F])(\\d{3})([\\dA-FHJ-NPR-Y])\$".toRegex()

    private val eighteens = listOf("+")
    private val nineteens = listOf("-", "U", "V", "W", "X", "Y")
    private val twenties = listOf("A", "B", "C", "D", "E", "F")

    private val checkDigits = "0123456789ABCDEFHJKLMNPRSTUVWXY".toCharArray()

    fun String.isValidHenkilotunnus(): Boolean {
        try {
            val matchResult = hetuRegex.find(this.uppercase())
            if (matchResult == null) {
                logger.warn { "Invalid format in henkilotunnus." }
                return false
            }

            val (day, month, year, separator, index, checkDigit) = matchResult.destructured

            return isValidDate(day, month, year, separator.toCentury()) &&
                isValidCheckDigit("$day$month$year$index", checkDigit.first())
        } catch (e: DateTimeParseException) {
            logger.warn(e) { "Invalid date in henkilotunnus." }
            return false
        } catch (e: Exception) {
            logger.error(e) { "Error while validating henkilotunnus." }
            return false
        }
    }

    private fun isValidDate(day: String, month: String, year: String, century: Int): Boolean {
        val date = LocalDate.parse("$century$year-$month-$day")
        val result = date.isAfter(LocalDate.parse("1850-01-01")) && date.isBefore(LocalDate.now())
        if (!result) {
            logger.warn { "Henkilotunnus date was ancient or in the future: $date" }
        }
        return result
    }

    private fun isValidCheckDigit(parts: String, checkDigit: Char): Boolean {
        val index = parts.toInt() % 31
        val result = checkDigits[index] == checkDigit
        if (!result) {
            logger.warn { "Henkilotunnus check digit was not valid." }
        }
        return result
    }

    private fun String.toCentury(): Int =
        when (this) {
            in eighteens -> 18
            in nineteens -> 19
            in twenties -> 20
            else -> throw IllegalArgumentException("Invalid separator in henkilotunnus.")
        }
}

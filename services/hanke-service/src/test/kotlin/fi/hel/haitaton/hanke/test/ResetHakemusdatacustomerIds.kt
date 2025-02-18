package fi.hel.haitaton.hanke.test

import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
import java.util.UUID

private val fixedUUID = UUID.fromString("789b38cf-5345-4889-a5b8-2711c47559c8")

/**
 * The created entities for customers and contacts are in a different table in the database, so the
 * IDs cannot be the same for the taydennys and hakemus.
 *
 * Otherwise, the data should be identical, so the easiest way to compare them is to reset all the
 * IDs in both of them to a known fixed value and them see if they're equal.
 */
fun HakemusData.resetCustomerIds(): HakemusData {
    val customer = customerWithContacts?.resetIds()
    return when (this) {
        is JohtoselvityshakemusData -> {
            val contractor = contractorWithContacts?.resetIds()
            val representative = representativeWithContacts?.resetIds()
            val developer = propertyDeveloperWithContacts?.resetIds()
            copy(
                customerWithContacts = customer,
                contractorWithContacts = contractor,
                representativeWithContacts = representative,
                propertyDeveloperWithContacts = developer,
            )
        }
        is KaivuilmoitusData -> {
            val contractor = contractorWithContacts?.resetIds()
            val representative = representativeWithContacts?.resetIds()
            val developer = propertyDeveloperWithContacts?.resetIds()
            copy(
                customerWithContacts = customer,
                contractorWithContacts = contractor,
                representativeWithContacts = representative,
                propertyDeveloperWithContacts = developer,
            )
        }
    }
}

private fun Hakemusyhteystieto.resetIds() =
    copy(id = fixedUUID, yhteyshenkilot = yhteyshenkilot.map { it.copy(id = fixedUUID) })

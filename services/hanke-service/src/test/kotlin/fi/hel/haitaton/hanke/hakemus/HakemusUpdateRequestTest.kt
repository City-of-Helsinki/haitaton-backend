package fi.hel.haitaton.hanke.hakemus

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createBlankExcavationNotificationData
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createExcavationNotificationArea
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createTyoalue
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteyshenkiloFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankealueFactory.TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HakemusUpdateRequestTest {
    private val blankOriginal =
        ApplicationFactory.createBlankCableReportApplicationData()
            .toHakemusData(yhteystiedot = mapOf())

    private val blankRequest =
        HakemusUpdateRequestFactory.createBlankJohtoselvityshakemusUpdateRequest()

    @Nested
    inner class HasChanges {

        @Test
        fun `returns false when nothing has changed`() {
            val original = blankOriginal
            val request = blankRequest

            assertThat(request.hasChanges(original)).isFalse()
        }

        @Test
        fun `returns true when name is changed`() {
            val original = blankOriginal
            val request = blankRequest.copy(name = "Testihakemus")

            assertThat(request.hasChanges(original)).isTrue()
        }

        @Test
        fun `returns true when work description is changed`() {
            val original = blankOriginal
            val request =
                blankRequest.copy(
                    workDescription = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                )

            assertThat(request.hasChanges(original)).isTrue()
        }

        @Test
        fun `returns true when a customer is added`() {
            val original = blankOriginal
            val request =
                createJohtoselvityshakemusUpdateRequestWithCustomerWithContacts(
                    "cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f",
                )

            assertThat(request.hasChanges(original)).isTrue()
        }

        @Test
        fun `returns true when new customer contact is added`() {
            val original =
                createHakemusDataWithYhteystiedot(
                    Pair("cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f", true),
                )
            val request =
                createJohtoselvityshakemusUpdateRequestWithCustomerWithContacts(
                    "cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f",
                    "3047a6fc-5a2b-41cb-bb99-1f907fef2101",
                )

            assertThat(request.hasChanges(original)).isTrue()
        }

        @Test
        fun `returns false when customer contacts are the same`() {
            val original =
                createHakemusDataWithYhteystiedot(
                    Pair("cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f", true),
                    Pair("3047a6fc-5a2b-41cb-bb99-1f907fef2101", false),
                )
            val request =
                createJohtoselvityshakemusUpdateRequestWithCustomerWithContacts(
                    "cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f",
                    "3047a6fc-5a2b-41cb-bb99-1f907fef2101",
                )

            assertThat(request.hasChanges(original)).isFalse()
        }

        @Test
        fun `returns true when customer contacts are removed`() {
            val original =
                createHakemusDataWithYhteystiedot(
                    Pair("cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f", true),
                    Pair("3047a6fc-5a2b-41cb-bb99-1f907fef2101", false),
                )
            val request = createJohtoselvityshakemusUpdateRequestWithCustomerWithContacts()

            assertThat(request.hasChanges(original)).isTrue()
        }

        @Test
        fun `returns false when only tormaystarkastelu is missing in request`() {
            val original = createHakemusDataWithTormaystarkastelu()
            val request = createKaivuilmoitusUpdateRequestWithoutTormaystarkastelu()
            assertThat(request.hasChanges(original)).isFalse()
        }
    }

    private fun createHakemusDataWithYhteystiedot(
        vararg hankekayttajat: Pair<String, Boolean>
    ): HakemusData {
        val yhteyshenkilot =
            hankekayttajat.map { (hankekayttajaId, tilaaja) ->
                HakemusyhteyshenkiloFactory.create(
                    hankekayttajaId = UUID.fromString(hankekayttajaId), tilaaja = tilaaja)
            }

        val yhteystieto =
            HakemusyhteystietoFactory.create(
                tyyppi = CustomerType.COMPANY,
                rooli = ApplicationContactType.HAKIJA,
                nimi = "Testiyritys",
                sahkoposti = "info@testiyritys.fi",
                puhelinnumero = "0401234567",
                registryKey = "1234567-8",
                yhteyshenkilot = yhteyshenkilot,
            )

        return blankOriginal.copy(customerWithContacts = yhteystieto)
    }

    private fun createJohtoselvityshakemusUpdateRequestWithCustomerWithContacts(
        vararg hankekayttajaIds: String
    ): JohtoselvityshakemusUpdateRequest {
        val customer =
            CustomerRequest(
                type = CustomerType.COMPANY,
                name = "Testiyritys",
                email = "info@testiyritys.fi",
                phone = "0401234567",
                registryKey = "1234567-8",
            )
        val contacts = hankekayttajaIds.map { ContactRequest(UUID.fromString(it)) }
        val customerWithContacts = CustomerWithContactsRequest(customer, contacts)
        return blankRequest.copy(customerWithContacts = customerWithContacts)
    }

    private fun createHakemusDataWithTormaystarkastelu(): HakemusData {
        val tormaystarkasteluTulos =
            TormaystarkasteluTulos(
                TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU,
                3.0f,
                5.0f,
                5.0f,
            )
        val tyoalue = createTyoalue(tormaystarkasteluTulos = tormaystarkasteluTulos)
        val area = createExcavationNotificationArea(tyoalueet = listOf(tyoalue))
        return createBlankExcavationNotificationData()
            .copy(areas = listOf(area))
            .toHakemusData(mapOf())
    }

    private fun createKaivuilmoitusUpdateRequestWithoutTormaystarkastelu():
        KaivuilmoitusUpdateRequest {
        val area = createExcavationNotificationArea()
        return HakemusUpdateRequestFactory.createBlankKaivuilmoitusUpdateRequest()
            .copy(areas = listOf(area))
    }
}

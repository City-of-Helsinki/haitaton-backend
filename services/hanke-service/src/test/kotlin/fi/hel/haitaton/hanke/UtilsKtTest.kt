package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import fi.hel.haitaton.hanke.domain.BusinessId
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.asianHoitajaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.asianhoitajaApplicationContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createCableReportApplicationData
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.hakijaApplicationContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.hakijaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.rakennuttajaApplicationContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.rakennuttajaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.suorittajaApplicationContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.suorittajaCustomerContact
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UtilsKtTest {

    @Nested
    inner class ValidBusinessId {
        @ParameterizedTest
        @ValueSource(
            strings =
                [
                    "2182805-0",
                    "7126070-7",
                    "1164243-9",
                    "3227510-5",
                    "3362438-9",
                    "7743551-2",
                    "8634465-5",
                    "0407327-4",
                    "7542843-1",
                    "6545312-3"
                ]
        )
        fun `isValid when valid businessId returns true`(businessId: BusinessId) {
            assertTrue(businessId.isValidBusinessId())
        }

        @ParameterizedTest
        @ValueSource(
            strings =
                [
                    "21828053-0",
                    "71260-7",
                    "1164243-",
                    "3227510",
                    "3362438-4",
                    "0100007-1",
                    "823A445-7",
                    "8238445-A"
                ]
        )
        fun `isValid when not valid businessId returns false`(businessId: BusinessId) {
            assertFalse(businessId.isValidBusinessId())
        }
    }

    @Nested
    inner class ApplicationDataTypedContacts {

        @Test
        fun `typedContacts when all types are present should return all as typed`() {
            val applicationData =
                createCableReportApplicationData(
                    customerWithContacts = hakijaCustomerContact,
                    contractorWithContacts = suorittajaCustomerContact,
                    representativeWithContacts = asianHoitajaCustomerContact,
                    propertyDeveloperWithContacts = rakennuttajaCustomerContact
                )

            val result = applicationData.typedContacts()

            assertThat(result)
                .containsExactlyInAnyOrder(
                    hakijaApplicationContact,
                    suorittajaApplicationContact,
                    asianhoitajaApplicationContact,
                    rakennuttajaApplicationContact
                )
        }

        @Test
        fun `typedContacts when not all types present should return existing as typed`() {
            val applicationData =
                createCableReportApplicationData(
                    customerWithContacts = hakijaCustomerContact,
                    contractorWithContacts = suorittajaCustomerContact
                )

            val result = applicationData.typedContacts()

            assertThat(result)
                .containsExactlyInAnyOrder(hakijaApplicationContact, suorittajaApplicationContact)
        }

        @Test
        fun `removeInviter when inviter present filters inviter from contacts`() {
            val applicationData =
                createCableReportApplicationData(
                    customerWithContacts = hakijaCustomerContact,
                    contractorWithContacts = suorittajaCustomerContact
                )
            val inviter =
                HankeKayttajaFactory.createEntity(sahkoposti = suorittajaApplicationContact.email)

            val result = applicationData.typedContacts(omit = inviter.sahkoposti)

            assertThat(result).containsExactlyInAnyOrder(hakijaApplicationContact)
        }

        @Test
        fun `removeInviter when inviter is null does no filtering`() {
            val applicationData =
                createCableReportApplicationData(
                    customerWithContacts = hakijaCustomerContact,
                    contractorWithContacts = suorittajaCustomerContact,
                    representativeWithContacts = asianHoitajaCustomerContact,
                    propertyDeveloperWithContacts = rakennuttajaCustomerContact
                )

            val result = applicationData.typedContacts(omit = null)

            assertThat(result)
                .containsExactlyInAnyOrder(
                    hakijaApplicationContact,
                    suorittajaApplicationContact,
                    asianhoitajaApplicationContact,
                    rakennuttajaApplicationContact
                )
        }
    }
}

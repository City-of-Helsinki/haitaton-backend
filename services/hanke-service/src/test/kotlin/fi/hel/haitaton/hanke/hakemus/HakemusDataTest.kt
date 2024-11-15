package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasClass
import assertk.assertions.isEmpty
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteyshenkiloFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.PaperDecisionReceiverFactory
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EmptySource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource

class HakemusDataTest {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ListChanges {
        val base: JohtoselvityshakemusData = HakemusFactory.createJohtoselvityshakemusData()

        @Test
        fun `throws exception when classes don't match`() {
            val updated = HakemusFactory.createKaivuilmoitusData()

            val failure = assertFailure { base.listChanges(updated) }

            failure.all {
                hasClass(IncompatibleHakemusDataException::class)
                messageContains("Incompatible hakemus data when retrieving changes")
                messageContains("firstClass=JohtoselvityshakemusData")
                messageContains("secondClass=KaivuilmoitusData")
            }
        }

        @Test
        fun `returns empty when there are no changes`() {
            val updated = HakemusFactory.createJohtoselvityshakemusData()

            val result = base.listChanges(updated)

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns all changes when there are several`() {
            val updated = base.copy(name = "Other name", startTime = base.startTime!!.plusDays(3))

            val result = base.listChanges(updated)

            assertThat(result).containsExactly("name", "startTime")
        }

        private fun commonFieldCases(): List<Arguments> =
            listOf(
                Arguments.of(
                    base.copy(applicationType = ApplicationType.EXCAVATION_NOTIFICATION),
                    "applicationType",
                ),
                Arguments.of(base.copy(startTime = base.startTime!!.minusDays(2)), "startTime"),
                Arguments.of(base.copy(endTime = base.endTime!!.plusDays(2)), "endTime"),
                Arguments.of(
                    base.copy(paperDecisionReceiver = PaperDecisionReceiverFactory.default),
                    "paperDecisionReceiver",
                ),
                Arguments.of(
                    base.copy(customerWithContacts = HakemusyhteystietoFactory.create()),
                    "customerWithContacts",
                ),
            )

        @ParameterizedTest(name = "{displayName} {1}")
        @MethodSource("commonFieldCases")
        fun `returns changes when common fields have changes`(updated: HakemusData, name: String) {
            val changes = base.listChanges(updated)

            assertThat(changes).containsExactly(name)
        }

        @Nested
        inner class YhteystietoChanges {
            private val yhteyshenkilo1 = HakemusyhteyshenkiloFactory.create()
            private val yhteyshenkilo2 =
                HakemusyhteyshenkiloFactory.create(
                    etunimi = "Joku",
                    sukunimi = "Toinen",
                    sahkoposti = "joku.toinen@email",
                )
            private val base: JohtoselvityshakemusData =
                this@ListChanges.base.copy(
                    customerWithContacts =
                        HakemusyhteystietoFactory.create(
                            yhteyshenkilot = listOf(yhteyshenkilo1, yhteyshenkilo2)
                        )
                )

            private val withOneHenkilo =
                base.copy(
                    customerWithContacts =
                        base.customerWithContacts!!.copy(yhteyshenkilot = listOf(yhteyshenkilo1))
                )

            @Test
            fun `returns a change when yhteyshenkilo removed from yhteystieto`() {
                val result = base.listChanges(withOneHenkilo)

                assertThat(result).containsExactly("customerWithContacts")
            }

            @Test
            fun `returns a change when yhteyshenkilo added to yhteystieto`() {
                val result = withOneHenkilo.listChanges(base)

                assertThat(result).containsExactly("customerWithContacts")
            }

            @Test
            fun `returns a change when yhteyshenkilo changed in yhteystieto`() {
                val updatedYhteyshenkilo = yhteyshenkilo1.copy(etunimi = "Muutettu")
                val updated =
                    base.copy(
                        customerWithContacts =
                            base.customerWithContacts!!.copy(
                                yhteyshenkilot = listOf(yhteyshenkilo1, updatedYhteyshenkilo)
                            )
                    )

                val result = base.listChanges(updated)

                assertThat(result).containsExactly("customerWithContacts")
            }
        }

        @Nested
        inner class AreaChanges {
            private val dataWithTwoAreas =
                base.copy(
                    areas =
                        listOf(
                            ApplicationFactory.createCableReportApplicationArea(name = "first"),
                            ApplicationFactory.createCableReportApplicationArea(name = "second"),
                        )
                )

            @ParameterizedTest
            @EmptySource
            @NullSource
            fun `returns a change when areas were empty but new ones are added`(
                areas: List<JohtoselvitysHakemusalue>?
            ) {
                val base = base.copy(areas = areas)

                val result = base.listChanges(dataWithTwoAreas)

                assertThat(result).containsExactly("areas[0]", "areas[1]")
            }

            @ParameterizedTest
            @EmptySource
            @NullSource
            fun `returns a change when there were areas but now they are empty`(
                areas: List<JohtoselvitysHakemusalue>?
            ) {
                val updated = base.copy(areas = areas)

                val result = dataWithTwoAreas.listChanges(updated)

                assertThat(result).containsExactly("areas[0]", "areas[1]")
            }

            @Test
            fun `returns several changes when an area is removed from the middle`() {
                val base =
                    base.copy(
                        areas =
                            listOf(
                                ApplicationFactory.createCableReportApplicationArea(name = "first"),
                                ApplicationFactory.createCableReportApplicationArea(
                                    name = "removed1"
                                ),
                                ApplicationFactory.createCableReportApplicationArea(
                                    name = "removed2"
                                ),
                                ApplicationFactory.createCableReportApplicationArea(name = "second"),
                            )
                    )

                val result = base.listChanges(dataWithTwoAreas)

                assertThat(result).containsExactly("areas[1]", "areas[2]", "areas[3]")
            }

            @Test
            fun `returns several changes when an area is added to the middle`() {
                val updated =
                    base.copy(
                        areas =
                            listOf(
                                ApplicationFactory.createCableReportApplicationArea(name = "first"),
                                ApplicationFactory.createCableReportApplicationArea(
                                    name = "added1"
                                ),
                                ApplicationFactory.createCableReportApplicationArea(
                                    name = "added2"
                                ),
                                ApplicationFactory.createCableReportApplicationArea(name = "second"),
                            )
                    )

                val result = dataWithTwoAreas.listChanges(updated)

                assertThat(result).containsExactly("areas[1]", "areas[2]", "areas[3]")
            }
        }

        private fun johtoselvitysCases(): List<Arguments> =
            listOf(
                // One shared field to test that the super method gets called.
                Arguments.of(base.copy(startTime = base.startTime!!.minusDays(1)), "startTime"),
                Arguments.of(
                    base.copy(postalAddress = ApplicationFactory.createPostalAddress()),
                    "postalAddress",
                ),
                Arguments.of(base.copy(constructionWork = true), "constructionWork"),
                Arguments.of(base.copy(maintenanceWork = true), "maintenanceWork"),
                Arguments.of(base.copy(propertyConnectivity = true), "propertyConnectivity"),
                Arguments.of(base.copy(emergencyWork = true), "emergencyWork"),
                Arguments.of(base.copy(rockExcavation = true), "rockExcavation"),
                Arguments.of(base.copy(workDescription = "New description"), "workDescription"),
                Arguments.of(
                    base.copy(contractorWithContacts = HakemusyhteystietoFactory.create()),
                    "contractorWithContacts",
                ),
                Arguments.of(
                    base.copy(propertyDeveloperWithContacts = HakemusyhteystietoFactory.create()),
                    "propertyDeveloperWithContacts",
                ),
                Arguments.of(
                    base.copy(representativeWithContacts = HakemusyhteystietoFactory.create()),
                    "representativeWithContacts",
                ),
            )

        @ParameterizedTest(name = "{displayName} {1}")
        @MethodSource("johtoselvitysCases")
        fun `returns changes when johtoselvityshakemus fields have changes`(
            updated: JohtoselvityshakemusData,
            name: String,
        ) {
            val changes = base.listChanges(updated)

            assertThat(changes).containsExactly(name)
        }

        private fun kaivuilmoitusCases(): List<Arguments> {
            val base = HakemusFactory.createKaivuilmoitusData()
            return listOf(
                // One shared field to test that the super method gets called.
                Arguments.of(base.copy(startTime = base.startTime!!.minusDays(1)), "startTime"),
                Arguments.of(base.copy(workDescription = "New description."), "workDescription"),
                Arguments.of(base.copy(constructionWork = true), "constructionWork"),
                Arguments.of(base.copy(maintenanceWork = true), "maintenanceWork"),
                Arguments.of(base.copy(emergencyWork = true), "emergencyWork"),
                Arguments.of(base.copy(cableReportDone = !base.cableReportDone), "cableReportDone"),
                Arguments.of(base.copy(rockExcavation = true), "rockExcavation"),
                Arguments.of(base.copy(cableReports = listOf("JS2400001")), "cableReports"),
                Arguments.of(
                    base.copy(placementContracts = listOf("tunnus")),
                    "placementContracts",
                ),
                Arguments.of(base.copy(requiredCompetence = true), "requiredCompetence"),
                Arguments.of(
                    base.copy(contractorWithContacts = HakemusyhteystietoFactory.create()),
                    "contractorWithContacts",
                ),
                Arguments.of(
                    base.copy(propertyDeveloperWithContacts = HakemusyhteystietoFactory.create()),
                    "propertyDeveloperWithContacts",
                ),
                Arguments.of(
                    base.copy(representativeWithContacts = HakemusyhteystietoFactory.create()),
                    "representativeWithContacts",
                ),
                Arguments.of(
                    base.copy(
                        invoicingCustomer = HakemusyhteystietoFactory.createLaskutusyhteystieto()
                    ),
                    "invoicingCustomer",
                ),
                Arguments.of(base.copy(additionalInfo = "Uutta lisätietoa"), "additionalInfo"),
            )
        }

        @ParameterizedTest(name = "{displayName} {1}")
        @MethodSource("kaivuilmoitusCases")
        fun `returns changes when kaivuilmoitus fields have changes`(
            updated: KaivuilmoitusData,
            name: String,
        ) {
            val base = HakemusFactory.createKaivuilmoitusData()

            val changes = base.listChanges(updated)

            assertThat(changes).containsExactly(name)
        }
    }
}

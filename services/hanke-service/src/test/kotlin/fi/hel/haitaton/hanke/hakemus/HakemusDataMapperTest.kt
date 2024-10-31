package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.each
import assertk.assertions.hasClass
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import fi.hel.haitaton.hanke.allu.AlluExcavationNotificationData
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.allu.PostalAddress as AlluPostalAddress
import fi.hel.haitaton.hanke.allu.StreetAddress as AlluStreetAddress
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory.withYhteyshenkilo
import fi.hel.haitaton.hanke.geometria.UnsupportedCoordinateSystemException
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.getGeometries
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluCableReportData
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluCustomer
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluData
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluExcavationNotificationData
import org.geojson.GeoJsonObject
import org.geojson.Polygon
import org.geojson.jackson.CrsType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EmptySource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource

class HakemusDataMapperTest {

    @Nested
    inner class ToAlluData {
        @Test
        fun `returns a cable report when called with a cable report`() {
            val hakemus =
                HakemusFactory.createJohtoselvityshakemusData(
                    customerWithContacts = HakemusyhteystietoFactory.create(),
                    contractorWithContacts = HakemusyhteystietoFactory.create(),
                )
            val hanketunnus = "HAI23-0009"

            val response = hakemus.toAlluData(hanketunnus)

            assertThat(response).isInstanceOf(AlluCableReportApplicationData::class).all {
                prop(AlluCableReportApplicationData::identificationNumber).isEqualTo(hanketunnus)
            }
        }

        @Test
        fun `returns an excavation notification when called with an excavation notification`() {
            val hakemus =
                HakemusFactory.createKaivuilmoitusData(
                    customerWithContacts = HakemusyhteystietoFactory.create(),
                    contractorWithContacts = HakemusyhteystietoFactory.create(),
                )
            val hanketunnus = "HAI23-0019"

            val response = hakemus.toAlluData(hanketunnus)

            assertThat(response).isInstanceOf(AlluExcavationNotificationData::class).all {
                prop(AlluExcavationNotificationData::identificationNumber).isEqualTo(hanketunnus)
            }
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ToAlluCableReportData {
        val hakemus =
            HakemusFactory.createJohtoselvityshakemusData(
                customerWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                contractorWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                postalAddress = PostalAddress(StreetAddress("Katu 1"), "00980", "Helsinki"),
            )

        @Test
        fun `returns the corresponding Allu data with correct info`() {
            val result = hakemus.toAlluCableReportData("HAI23-9")

            val expected =
                AlluCableReportApplicationData(
                    identificationNumber = "HAI23-9",
                    pendingOnClient = false,
                    name = hakemus.name,
                    postalAddress =
                        AlluPostalAddress(
                            AlluStreetAddress(hakemus.postalAddress!!.streetAddress.streetName),
                            hakemus.postalAddress.postalCode,
                            hakemus.postalAddress.city,
                        ),
                    constructionWork = hakemus.constructionWork,
                    maintenanceWork = hakemus.maintenanceWork,
                    propertyConnectivity = hakemus.propertyConnectivity,
                    emergencyWork = hakemus.emergencyWork,
                    workDescription = hakemus.workDescription + "\nEi louhita",
                    clientApplicationKind = hakemus.workDescription + "\nEi louhita",
                    startTime = hakemus.startTime!!,
                    endTime = hakemus.endTime!!,
                    geometry = hakemus.getGeometries(),
                    area = null,
                    customerWithContacts = hakemus.customerWithContacts!!.toAlluCustomer(),
                    contractorWithContacts = hakemus.contractorWithContacts!!.toAlluCustomer(),
                    propertyDeveloperWithContacts = null,
                    representativeWithContacts = null,
                    invoicingCustomer = null,
                    customerReference = null,
                    trafficArrangementImages = null,
                )
            assertThat(result).isEqualTo(expected)
        }

        @ParameterizedTest(name = "{1} is null")
        @MethodSource("nullCases")
        fun `throws an exception when a required field is null`(
            hakemus: JohtoselvityshakemusData,
            path: String,
        ) {
            val failure = assertFailure { hakemus.toAlluCableReportData("") }

            failure.all {
                hasClass(AlluDataException::class)
                messageContains("Application data failed validation at applicationData.$path")
                messageContains("Can't be null")
            }
        }

        private fun nullCases() =
            listOf(
                Arguments.of(hakemus.copy(customerWithContacts = null), "customerWithContacts"),
                Arguments.of(hakemus.copy(contractorWithContacts = null), "contractorWithContacts"),
                Arguments.of(hakemus.copy(startTime = null), "startTime"),
                Arguments.of(hakemus.copy(endTime = null), "endTime"),
                Arguments.of(hakemus.copy(rockExcavation = null), "rockExcavation"),
            )
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ToAlluExcavationNotificationData {
        private val hakemus =
            HakemusFactory.createKaivuilmoitusData(
                cableReports = listOf("JS2400001", "JS2400002"),
                placementContracts = listOf("SL2400001", "SL2400002"),
                areas =
                    listOf(
                        ApplicationFactory.createExcavationNotificationArea(katuosoite = "Katu 1"),
                        ApplicationFactory.createExcavationNotificationArea(katuosoite = "Kuja 3"),
                        ApplicationFactory.createExcavationNotificationArea(katuosoite = "Katu 1"),
                    ),
                customerWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                contractorWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                propertyDeveloperWithContacts =
                    HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                representativeWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                invoicingCustomer =
                    HakemusyhteystietoFactory.createLaskutusyhteystieto(
                        asiakkaanViite = "Hanke #521",
                        katuosoite = "Laskukatu 41",
                        postinumero = "00410",
                        postitoimipaikka = "Helsinki",
                    ),
                additionalInfo = "Lisätietoja hakemusesta.",
            )

        private val expectedResult =
            AlluExcavationNotificationData(
                identificationNumber = "HAI23-0045",
                pendingOnClient = false,
                name = hakemus.name,
                workPurpose = hakemus.workDescription,
                clientApplicationKind = hakemus.workDescription,
                constructionWork = hakemus.constructionWork,
                maintenanceWork = hakemus.maintenanceWork,
                emergencyWork = hakemus.emergencyWork,
                cableReports = hakemus.cableReports,
                placementContracts = hakemus.placementContracts,
                startTime = hakemus.startTime!!,
                endTime = hakemus.endTime!!,
                geometry = hakemus.getGeometries(),
                area = null,
                postalAddress = AlluPostalAddress(AlluStreetAddress("Katu 1, Kuja 3"), "", ""),
                customerWithContacts = hakemus.customerWithContacts!!.toAlluCustomer(),
                contractorWithContacts = hakemus.contractorWithContacts!!.toAlluCustomer(),
                propertyDeveloperWithContacts =
                    hakemus.propertyDeveloperWithContacts!!.toAlluCustomer(),
                representativeWithContacts = hakemus.representativeWithContacts!!.toAlluCustomer(),
                invoicingCustomer = hakemus.invoicingCustomer!!.toAlluData(),
                customerReference = "Hanke #521",
                additionalInfo = hakemus.additionalInfo,
                pksCard = null,
                selfSupervision = null,
                propertyConnectivity = null,
                trafficArrangementImages = null,
                trafficArrangements = null,
                trafficArrangementImpediment = null,
            )

        @Test
        fun `returns the corresponding Allu data with correct info`() {
            val result = hakemus.toAlluExcavationNotificationData("HAI23-0045")

            assertThat(result).isEqualTo(expectedResult)
        }

        @ParameterizedTest(name = "{1} is null")
        @MethodSource("requiredNullCases")
        fun `throws an exception when a required field is null`(
            hakemus: KaivuilmoitusData,
            path: String,
            error: AlluDataError,
        ) {
            val failure = assertFailure { hakemus.toAlluExcavationNotificationData("") }

            failure.all {
                hasClass(AlluDataException::class)
                messageContains("Application data failed validation at applicationData.$path")
                messageContains(error.toString())
            }
        }

        private fun requiredNullCases() =
            listOf(
                Arguments.of(
                    hakemus.copy(customerWithContacts = null),
                    "customerWithContacts",
                    AlluDataError.NULL,
                ),
                Arguments.of(
                    hakemus.copy(contractorWithContacts = null),
                    "contractorWithContacts",
                    AlluDataError.NULL,
                ),
                Arguments.of(hakemus.copy(startTime = null), "startTime", AlluDataError.NULL),
                Arguments.of(hakemus.copy(endTime = null), "endTime", AlluDataError.NULL),
                Arguments.of(hakemus.copy(areas = null), "areas", AlluDataError.EMPTY_OR_NULL),
            )

        @ParameterizedTest
        @MethodSource("optionalNullCases")
        fun `works as normal when an optional field is null`(
            hakemus: KaivuilmoitusData,
            expected: AlluExcavationNotificationData,
        ) {
            val result = hakemus.toAlluExcavationNotificationData("HAI23-0045")

            assertThat(result).isEqualTo(expected)
        }

        private fun optionalNullCases() =
            listOf(
                Arguments.of(
                    hakemus.copy(representativeWithContacts = null),
                    expectedResult.copy(representativeWithContacts = null),
                ),
                Arguments.of(
                    hakemus.copy(propertyDeveloperWithContacts = null),
                    expectedResult.copy(propertyDeveloperWithContacts = null),
                ),
                Arguments.of(
                    hakemus.copy(invoicingCustomer = null),
                    expectedResult.copy(invoicingCustomer = null, customerReference = null),
                ),
                Arguments.of(
                    hakemus.copy(
                        invoicingCustomer =
                            HakemusyhteystietoFactory.createLaskutusyhteystieto(
                                sahkoposti = null,
                                puhelinnumero = null,
                                registryKey = null,
                                ovttunnus = null,
                                valittajanTunnus = null,
                                asiakkaanViite = null,
                                katuosoite = null,
                                postinumero = null,
                                postitoimipaikka = null,
                            )
                    ),
                    expectedResult.copy(
                        invoicingCustomer =
                            Customer(
                                type = CustomerType.COMPANY,
                                name = hakemus.invoicingCustomer!!.nimi,
                                postalAddress = null,
                                email = null,
                                phone = null,
                                registryKey = null,
                                ovt = null,
                                invoicingOperator = null,
                                country = "FI",
                                sapCustomerNumber = null,
                            ),
                        customerReference = null,
                    ),
                ),
                Arguments.of(
                    hakemus.copy(
                        invoicingCustomer =
                            HakemusyhteystietoFactory.createLaskutusyhteystieto(
                                katuosoite = "Katu 1",
                                postinumero = null,
                                postitoimipaikka = null,
                            )
                    ),
                    expectedResult.copy(
                        invoicingCustomer =
                            expectedResult.invoicingCustomer!!.copy(
                                postalAddress =
                                    AlluPostalAddress(AlluStreetAddress("Katu 1"), "", "")
                            ),
                        customerReference = null,
                    ),
                ),
                Arguments.of(
                    hakemus.copy(
                        invoicingCustomer =
                            HakemusyhteystietoFactory.createLaskutusyhteystieto(
                                katuosoite = null,
                                postinumero = "00410",
                                postitoimipaikka = "Helsinki",
                            )
                    ),
                    expectedResult.copy(
                        invoicingCustomer =
                            expectedResult.invoicingCustomer.copy(postalAddress = null),
                        customerReference = null,
                    ),
                ),
                Arguments.of(
                    hakemus.copy(placementContracts = null),
                    expectedResult.copy(placementContracts = null),
                ),
                Arguments.of(
                    hakemus.copy(cableReports = null),
                    expectedResult.copy(cableReports = null),
                ),
            )
    }

    @Nested
    inner class GetGeometries {
        val hakemus = HakemusFactory.createJohtoselvityshakemusData()

        @ParameterizedTest
        @EmptySource
        @NullSource
        fun `throws exception when there are no areas`(areas: List<JohtoselvitysHakemusalue>?) {
            val hakemus = hakemus.copy(areas = areas)

            val failure = assertFailure { hakemus.getGeometries() }

            failure.all {
                hasClass(AlluDataException::class)
                messageContains("Application data failed validation at applicationData.areas")
                messageContains("Can't be empty or null")
            }
        }

        @Test
        fun `throws exception when one of the geometries has the wrong coordinate system`() {
            val area = ApplicationFactory.createCableReportApplicationArea()
            area.geometry.crs.properties["name"] = "Wrong geometry"
            val hakemus =
                hakemus.copy(
                    areas = listOf(area, ApplicationFactory.createCableReportApplicationArea())
                )

            val failure = assertFailure { hakemus.getGeometries() }

            failure.all {
                hasClass(UnsupportedCoordinateSystemException::class)
                messageContains("Invalid coordinate system")
                messageContains("Wrong geometry")
            }
        }

        @Test
        fun `moves the CRS from inside the polygons to the geometry collection`() {
            val areas =
                listOf(
                    ApplicationFactory.createCableReportApplicationArea(
                        geometry = GeometriaFactory.secondPolygon()
                    ),
                    ApplicationFactory.createCableReportApplicationArea(
                        geometry = GeometriaFactory.thirdPolygon()
                    ),
                )
            val hakemus = hakemus.copy(areas = areas)

            val result = hakemus.getGeometries()

            assertThat(result.crs.properties["name"]).isEqualTo(COORDINATE_SYSTEM_URN)
            assertThat(result.crs.type).isEqualTo(CrsType.name)
            assertThat(result.geometries).each { it.prop(GeoJsonObject::getCrs).isNull() }
        }

        @Test
        fun `combines the geometries from the areas to a single geometry collection`() {
            val areas =
                listOf(
                    ApplicationFactory.createCableReportApplicationArea(
                        geometry = GeometriaFactory.secondPolygon()
                    ),
                    ApplicationFactory.createCableReportApplicationArea(
                        geometry = GeometriaFactory.thirdPolygon()
                    ),
                )
            val hakemus = hakemus.copy(areas = areas)

            val result = hakemus.getGeometries()

            assertThat(result.geometries.map { it as Polygon }.map { it.coordinates })
                .containsExactly(
                    GeometriaFactory.secondPolygon().coordinates,
                    GeometriaFactory.thirdPolygon().coordinates,
                )
        }
    }
}

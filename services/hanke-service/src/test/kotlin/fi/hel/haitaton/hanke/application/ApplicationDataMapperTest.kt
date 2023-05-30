package fi.hel.haitaton.hanke.application

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.endsWith
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.CustomerWithContacts as AlluCustomerWithContacts
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.geometria.UnsupportedCoordinateSystemException
import org.geojson.Polygon
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

private const val HANKE_TUNNUS = "HAI23-45"

internal class ApplicationDataMapperTest {

    @Nested
    inner class ToAlluData {

        @Test
        fun `toAlluData when cable report should map fields correctly`() {
            val input = AlluDataFactory.createCableReportApplicationData()

            val result = ApplicationDataMapper.toAlluData(HANKE_TUNNUS, input)

            assertThat(result.name).isEqualTo(input.name)
            assertThat(result.customerWithContacts).isValid(input.customerWithContacts)
            assertThat(result.geometry).isEqualTo(ApplicationDataMapper.getGeometry(input))
            assertThat(result.startTime).isEqualTo(input.startTime)
            assertThat(result.endTime).isEqualTo(input.endTime)
            assertThat(result.pendingOnClient).isEqualTo(input.pendingOnClient)
            assertThat(result.identificationNumber).isEqualTo(HANKE_TUNNUS)
            assertThat(result.clientApplicationKind).isEqualTo(toDescription(input.workDescription))
            assertThat(result.workDescription).isEqualTo(toDescription(input.workDescription))
            assertThat(result.contractorWithContacts).isValid(input.contractorWithContacts)
            assertThat(result.postalAddress).isEqualTo(input.postalAddress)
            assertThat(result.representativeWithContacts).isNull()
            assertThat(result.invoicingCustomer).isNull()
            assertThat(result.customerReference).isEqualTo(input.customerReference)
            assertThat(result.area).isEqualTo(input.area)
            assertThat(result.propertyDeveloperWithContacts).isNull()
            assertThat(result.constructionWork).isEqualTo(input.constructionWork)
            assertThat(result.maintenanceWork).isEqualTo(input.maintenanceWork)
            assertThat(result.emergencyWork).isEqualTo(input.emergencyWork)
            assertThat(result.propertyConnectivity).isEqualTo(input.propertyConnectivity)
        }

        @ParameterizedTest(name = "Adds rock excavation to work description, {argumentsWithNames}")
        @CsvSource("true,Louhitaan", "false,Ei louhita")
        fun `Adds rock excavation to work description`(
            rockExcavation: Boolean,
            expectedSuffix: String
        ) {
            val applicationData =
                AlluDataFactory.createCableReportApplicationData(rockExcavation = rockExcavation)

            val alluData = ApplicationDataMapper.toAlluData(HANKE_TUNNUS, applicationData)

            assertThat(alluData.workDescription).endsWith(expectedSuffix)
        }

        private fun Assert<AlluCustomerWithContacts>.isValid(input: CustomerWithContacts) =
            given { result ->
                assertThat(result.customer.type).isEqualTo(input.customer.type)
                assertThat(result.customer.country).isEqualTo(input.customer.country)
                assertThat(result.customer.email).isEqualTo(input.customer.email)
                assertThat(result.customer.phone).isEqualTo(input.customer.phone)
                assertThat(result.customer.registryKey).isEqualTo(input.customer.registryKey)
                assertThat(result.customer.ovt).isEqualTo(input.customer.ovt)
                assertThat(result.customer.invoicingOperator)
                    .isEqualTo(input.customer.invoicingOperator)
                assertThat(result.customer.sapCustomerNumber)
                    .isEqualTo(input.customer.sapCustomerNumber)
                assertThat(result.contacts).areValid()
            }

        private fun Assert<List<Contact>>.areValid() = each { contact ->
            contact.transform { it.name }.isNotNull()
            contact.transform { it.email }.isNotNull()
            contact.transform { it.phone }.isNotNull()
            contact.transform { it.orderer }.isNotNull()
        }

        private fun toDescription(initial: String) = "$initial\nEi louhita"
    }

    @Nested
    inner class GetGeometry {
        private val areaName = "test area"
        private val polygon: Polygon =
            "/fi/hel/haitaton/hanke/geometria/polygon.json".asJsonResource()
        private val otherPolygon: Polygon =
            "/fi/hel/haitaton/hanke/geometria/toinen_polygoni.json".asJsonResource()
        private val thirdPolygon: Polygon =
            "/fi/hel/haitaton/hanke/geometria/kolmas_polygoni.json".asJsonResource()

        @Test
        fun `uses areas if they are present`() {
            val applicationData =
                AlluDataFactory.createCableReportApplicationData(
                    areas = listOf(ApplicationArea(areaName, polygon)),
                )

            val geometry = ApplicationDataMapper.getGeometry(applicationData)

            assertThat(geometry.geometries).all {
                hasSize(1)
                extracting { (it as Polygon).coordinates }.containsExactly(polygon.coordinates)
            }
        }

        @Test
        fun `throws exception if areas are not present`() {
            val applicationData = AlluDataFactory.createCableReportApplicationData(areas = null)

            val exception =
                assertThrows<AlluDataException> {
                    ApplicationDataMapper.getGeometry(applicationData)
                }

            assertThat(exception.message)
                .isEqualTo(
                    "Application data failed validation at applicationData.areas: Can't be empty or null"
                )
        }

        @Test
        fun `forms geometries from all areas`() {
            val applicationData =
                AlluDataFactory.createCableReportApplicationData(
                    areas =
                        listOf(
                            ApplicationArea(areaName, polygon),
                            ApplicationArea("other area", otherPolygon),
                            ApplicationArea("third area", thirdPolygon),
                        ),
                )

            val geometry = ApplicationDataMapper.getGeometry(applicationData)

            assertThat(geometry.geometries).all {
                hasSize(3)
                extracting { (it as Polygon).coordinates }
                    .containsExactlyInAnyOrder(
                        polygon.coordinates,
                        otherPolygon.coordinates,
                        thirdPolygon.coordinates
                    )
            }
        }

        @Test
        fun `remove crs from the geometries`() {
            val applicationData =
                AlluDataFactory.createCableReportApplicationData(
                    areas = listOf(ApplicationArea(areaName, polygon))
                )
            assertThat(polygon.crs).isNotNull()

            val geometry = ApplicationDataMapper.getGeometry(applicationData)

            assertThat(geometry.geometries).hasSize(1)
            assertThat(geometry.geometries[0].crs).isNull()
        }

        @Test
        fun `add crs to the geometry collection`() {
            val applicationData =
                AlluDataFactory.createCableReportApplicationData(
                    areas = listOf(ApplicationArea(areaName, polygon))
                )

            val geometry = ApplicationDataMapper.getGeometry(applicationData)

            assertThat(geometry.crs).isNotNull()
            assertThat(geometry.crs.properties["name"]).isEqualTo(COORDINATE_SYSTEM_URN)
        }

        @Test
        fun `throws exception for invalid crs`() {
            val polygon: Polygon = "/fi/hel/haitaton/hanke/geometria/polygon.json".asJsonResource()
            polygon.crs = polygon.crs.apply { this.properties["name"] = "InvalidCode" }
            val applicationData =
                AlluDataFactory.createCableReportApplicationData(
                    areas = listOf(ApplicationArea(areaName, polygon))
                )

            val exception =
                assertThrows<UnsupportedCoordinateSystemException> {
                    ApplicationDataMapper.getGeometry(applicationData)
                }

            assertThat(exception.message).isEqualTo("Invalid coordinate system: InvalidCode")
        }
    }
}

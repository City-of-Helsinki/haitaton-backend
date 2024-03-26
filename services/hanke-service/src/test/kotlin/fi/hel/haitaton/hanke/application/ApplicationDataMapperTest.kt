package fi.hel.haitaton.hanke.application

import assertk.Assert
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.endsWith
import assertk.assertions.extracting
import assertk.assertions.hasMessage
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import fi.hel.haitaton.hanke.allu.Contact as AlluContact
import fi.hel.haitaton.hanke.allu.Customer as AlluCustomer
import fi.hel.haitaton.hanke.allu.CustomerWithContacts as AlluCustomerWithContacts
import fi.hel.haitaton.hanke.allu.PostalAddress as AlluPostalAddress
import fi.hel.haitaton.hanke.allu.StreetAddress as AlluStreetAddress
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.geometria.UnsupportedCoordinateSystemException
import org.geojson.GeoJsonObject
import org.geojson.Polygon
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

private const val HANKE_TUNNUS = "HAI23-45"

internal class ApplicationDataMapperTest {

    @Nested
    inner class ToAlluData {

        @Test
        fun `Map cable report fields correctly`() {
            val input = ApplicationFactory.createCableReportApplicationData()

            val result = ApplicationDataMapper.toAlluData(HANKE_TUNNUS, input)

            assertThat(result.name).isEqualTo(input.name)
            assertThat(result.customerWithContacts).isEqualTo(input.customerWithContacts!!)
            assertThat(result.geometry).isEqualTo(ApplicationDataMapper.getGeometry(input))
            assertThat(result.startTime).isEqualTo(input.startTime)
            assertThat(result.endTime).isEqualTo(input.endTime)
            assertThat(result.pendingOnClient).isEqualTo(input.pendingOnClient)
            assertThat(result.identificationNumber).isEqualTo(HANKE_TUNNUS)
            assertThat(result.clientApplicationKind).isEqualTo(toDescription(input.workDescription))
            assertThat(result.workDescription).isEqualTo(toDescription(input.workDescription))
            assertThat(result.contractorWithContacts).isEqualTo(input.contractorWithContacts!!)
            assertThat(result.postalAddress).isEqualTo(input.postalAddress)
            assertThat(result.representativeWithContacts).isNull()
            assertThat(result.invoicingCustomer).isNull()
            assertThat(result.customerReference).isNull()
            assertThat(result.area).isNull()
            assertThat(result.propertyDeveloperWithContacts).isNull()
            assertThat(result.constructionWork).isEqualTo(input.constructionWork)
            assertThat(result.maintenanceWork).isEqualTo(input.maintenanceWork)
            assertThat(result.emergencyWork).isEqualTo(input.emergencyWork)
            assertThat(result.propertyConnectivity).isEqualTo(input.propertyConnectivity)
        }

        @ParameterizedTest(name = "Adds rock excavation to work description, {argumentsWithNames}")
        @CsvSource("true,Louhitaan", "false,Ei louhita")
        fun `Adds rock excavation to cable report work description`(
            rockExcavation: Boolean,
            expectedSuffix: String
        ) {
            val applicationData =
                ApplicationFactory.createCableReportApplicationData(rockExcavation = rockExcavation)

            val alluData = ApplicationDataMapper.toAlluData(HANKE_TUNNUS, applicationData)

            assertThat(alluData.workDescription).endsWith(expectedSuffix)
        }

        @Test
        fun `Map excavation notification fields correctly`() {
            val input = ApplicationFactory.createExcavationNotificationApplicationData()

            val result = ApplicationDataMapper.toAlluData(HANKE_TUNNUS, input)

            assertThat(result.name).isEqualTo(input.name)
            assertThat(result.customerWithContacts).isEqualTo(input.customerWithContacts!!)
            assertThat(result.geometry).isEqualTo(ApplicationDataMapper.getGeometry(input))
            assertThat(result.startTime).isEqualTo(input.startTime)
            assertThat(result.endTime).isEqualTo(input.endTime)
            assertThat(result.pendingOnClient).isEqualTo(input.pendingOnClient)
            assertThat(result.identificationNumber).isEqualTo(HANKE_TUNNUS)
            assertThat(result.clientApplicationKind).isEqualTo(input.workDescription)
            assertThat(result.workPurpose).isEqualTo(input.workDescription)
            assertThat(result.contractorWithContacts).isEqualTo(input.contractorWithContacts!!)
            assertThat(result.postalAddress).isEqualTo(PostalAddress(StreetAddress(""), "", ""))
            assertThat(result.representativeWithContacts).isNull()
            assertThat(result.customerReference).isEqualTo(input.customerReference)
            assertThat(result.area).isNull()
            assertThat(result.propertyDeveloperWithContacts).isNull()
            assertThat(result.constructionWork).isEqualTo(input.constructionWork)
            assertThat(result.maintenanceWork).isEqualTo(input.maintenanceWork)
            assertThat(result.emergencyWork).isEqualTo(input.emergencyWork)
            assertThat(result.invoicingCustomer).isEqualTo(input.invoicingCustomer)
        }

        private fun Assert<AlluCustomerWithContacts>.isEqualTo(input: CustomerWithContacts) =
            given { result ->
                assertThat(result.customer.type).isEqualTo(input.customer.type)
                assertThat(result.customer.name).isEqualTo(input.customer.name)
                assertThat(result.customer.country).isEqualTo(DEFAULT_COUNTRY)
                assertThat(result.customer.email).isEqualTo(input.customer.email)
                assertThat(result.customer.phone).isEqualTo(input.customer.phone)
                assertThat(result.customer.registryKey).isEqualTo(input.customer.registryKey)
                assertThat(result.customer.ovt).isNull()
                assertThat(result.customer.invoicingOperator).isNull()
                assertThat(result.customer.sapCustomerNumber).isNull()
                assertThat(result.contacts).isEqualTo(input.contacts)
            }

        private fun Assert<List<AlluContact>>.isEqualTo(input: List<Contact>) = given { result ->
            assertThat(result).hasSize(input.size)
            result.zip(input).forEach { (alluContact, contact) ->
                assertThat(alluContact.name).isEqualTo(contact.fullName())
                assertThat(alluContact.email).isEqualTo(contact.email)
                assertThat(alluContact.phone).isEqualTo(contact.phone)
                assertThat(alluContact.orderer).isEqualTo(contact.orderer)
            }
        }

        private fun Assert<AlluCustomer?>.isEqualTo(input: InvoicingCustomer?) = given { result ->
            assertThat(result == null).isEqualTo(input == null)
            assertThat(result?.type).isEqualTo(input?.type)
            assertThat(result?.name).isEqualTo(input?.name)
            assertThat(result?.postalAddress).isEqualTo(input?.postalAddress)
            assertThat(result?.ovt).isEqualTo(input?.ovt)
            assertThat(result?.invoicingOperator).isEqualTo(input?.invoicingOperator)
            assertThat(result?.sapCustomerNumber).isNull()
            assertThat(result?.country).isEqualTo(DEFAULT_COUNTRY)
            assertThat(result?.email).isEqualTo(input?.email)
            assertThat(result?.phone).isEqualTo(input?.phone)
            assertThat(result?.registryKey).isEqualTo(input?.registryKey)
        }

        private fun Assert<AlluPostalAddress?>.isEqualTo(input: PostalAddress?) = given { result ->
            assertThat(result == null).isEqualTo(input == null)
            assertThat(result?.streetAddress).isEqualTo(input?.streetAddress)
            assertThat(result?.postalCode).isEqualTo(input?.postalCode)
            assertThat(result?.city).isEqualTo(input?.city)
        }

        private fun Assert<AlluStreetAddress?>.isEqualTo(input: StreetAddress?) = given { result ->
            assertThat(result == null).isEqualTo(input == null)
            assertThat(result?.streetName).isEqualTo(input?.streetName)
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
                ApplicationFactory.createCableReportApplicationData(
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
            val applicationData = ApplicationFactory.createCableReportApplicationData(areas = null)

            val exception = assertFailure { ApplicationDataMapper.getGeometry(applicationData) }

            exception.isInstanceOf(AlluDataException::class.java)
            exception.hasMessage(
                "Application data failed validation at applicationData.areas: Can't be empty or null"
            )
        }

        @Test
        fun `forms geometries from all areas`() {
            val applicationData =
                ApplicationFactory.createCableReportApplicationData(
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
                ApplicationFactory.createCableReportApplicationData(
                    areas = listOf(ApplicationArea(areaName, polygon))
                )
            assertThat(polygon.crs).isNotNull()

            val geometry = ApplicationDataMapper.getGeometry(applicationData)

            assertThat(geometry.geometries).single().prop(GeoJsonObject::getCrs).isNull()
        }

        @Test
        fun `add crs to the geometry collection`() {
            val applicationData =
                ApplicationFactory.createCableReportApplicationData(
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
                ApplicationFactory.createCableReportApplicationData(
                    areas = listOf(ApplicationArea(areaName, polygon))
                )

            val exception = assertFailure { ApplicationDataMapper.getGeometry(applicationData) }

            exception.isInstanceOf(UnsupportedCoordinateSystemException::class.java)
            exception.hasMessage("Invalid coordinate system: InvalidCode")
        }
    }
}

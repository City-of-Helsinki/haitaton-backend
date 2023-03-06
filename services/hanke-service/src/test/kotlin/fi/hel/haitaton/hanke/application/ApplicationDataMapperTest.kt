package fi.hel.haitaton.hanke.application

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.endsWith
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.geometria.UnsupportedCoordinateSystemException
import org.geojson.Polygon
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ApplicationDataMapperTest {

    @Nested
    inner class ToAlluData {
        @ParameterizedTest(name = "Adds rock excavation to work description, {argumentsWithNames}")
        @CsvSource("true,Louhitaan", "false,Ei louhita")
        fun `Adds rock excavation to work description`(
            rockExcavation: Boolean,
            expectedSuffix: String
        ) {
            val applicationData =
                AlluDataFactory.createCableReportApplicationData(rockExcavation = rockExcavation)

            val alluData = ApplicationDataMapper.toAlluData(applicationData)

            assertThat(alluData.workDescription).endsWith(expectedSuffix)
        }
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

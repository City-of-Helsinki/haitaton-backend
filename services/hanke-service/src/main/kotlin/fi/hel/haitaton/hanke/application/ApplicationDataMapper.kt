package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import fi.hel.haitaton.hanke.allu.AlluExcavationNotificationData
import fi.hel.haitaton.hanke.allu.PostalAddress
import fi.hel.haitaton.hanke.allu.StreetAddress
import fi.hel.haitaton.hanke.application.AlluDataError.EMPTY_OR_NULL
import fi.hel.haitaton.hanke.application.AlluDataError.NULL
import fi.hel.haitaton.hanke.geometria.UnsupportedCoordinateSystemException
import org.geojson.GeometryCollection
import org.geojson.Polygon

object ApplicationDataMapper {

    fun toAlluData(
        hankeTunnus: String,
        applicationData: CableReportApplicationData
    ): AlluCableReportApplicationData =
        with(applicationData) {
            val description = workDescription(this)
            AlluCableReportApplicationData(
                postalAddress = postalAddress?.toAlluData(),
                name = name,
                customerWithContacts =
                    customerWithContacts
                        .orThrow(path("customerWithContacts"))
                        .toAlluData(path("customerWithContacts")),
                representativeWithContacts =
                    representativeWithContacts?.toAlluData(path("representativeWithContacts")),
                invoicingCustomer = null,
                geometry = getGeometry(this),
                startTime = startTime.orThrow(path("startTime")),
                endTime = endTime.orThrow(path("endTime")),
                pendingOnClient = pendingOnClient,
                identificationNumber = hankeTunnus,
                customerReference = null,
                area = null, // currently area is not given nor calculated in Haitaton
                trafficArrangementImages = null,
                clientApplicationKind = description, // intentional
                workDescription = description,
                propertyDeveloperWithContacts =
                    propertyDeveloperWithContacts?.toAlluData(
                        path("propertyDeveloperWithContacts")
                    ),
                contractorWithContacts =
                    contractorWithContacts
                        .orThrow(path("contractorWithContacts"))
                        .toAlluData(path("contractorWithContacts")),
                constructionWork = constructionWork,
                maintenanceWork = maintenanceWork,
                emergencyWork = emergencyWork,
                propertyConnectivity = propertyConnectivity
            )
        }

    fun toAlluData(
        hankeTunnus: String,
        applicationData: ExcavationNotificationData
    ): AlluExcavationNotificationData =
        with(applicationData) {
            AlluExcavationNotificationData(
                postalAddress =
                    PostalAddress(
                        // TODO: this should be a combination of all area addresses (HAI-1542)
                        streetAddress = StreetAddress(""),
                        postalCode = "",
                        city = ""
                    ),
                name = name,
                customerWithContacts =
                    customerWithContacts
                        .orThrow(path("customerWithContacts"))
                        .toAlluData(path("customerWithContacts")),
                representativeWithContacts =
                    representativeWithContacts?.toAlluData(path("representativeWithContacts")),
                invoicingCustomer = invoicingCustomer?.toAlluData(path("invoicingCustomer")),
                geometry = getGeometry(this),
                startTime = startTime.orThrow(path("startTime")),
                endTime = endTime.orThrow(path("endTime")),
                pendingOnClient = pendingOnClient,
                identificationNumber = hankeTunnus,
                customerReference = customerReference,
                area = null, // currently area is not given nor calculated in Haitaton
                clientApplicationKind = workDescription,
                contractorWithContacts =
                    contractorWithContacts
                        .orThrow(path("contractorWithContacts"))
                        .toAlluData(path("contractorWithContacts")),
                propertyDeveloperWithContacts =
                    propertyDeveloperWithContacts?.toAlluData(
                        path("propertyDeveloperWithContacts")
                    ),
                pksCard = null,
                constructionWork = constructionWork,
                maintenanceWork = maintenanceWork,
                emergencyWork = emergencyWork,
                propertyConnectivity = null,
                workPurpose = workDescription,
                placementContracts = placementContracts?.toList(),
                cableReports = cableReports?.toList()
            )
        }

    /** If areas are missing, throw an exception. */
    internal fun getGeometry(applicationData: ApplicationData): GeometryCollection {
        val areas = applicationData.areas
        return if (areas.isNullOrEmpty()) {
            throw AlluDataException(path("areas"), EMPTY_OR_NULL)
        } else {
            // Check that all polygons have the coordinate reference system Haitaton understands
            areas
                .flatMap { area -> area.geometries().map { it.crs?.properties?.get("name") } }
                .find { it.toString() != COORDINATE_SYSTEM_URN }
                ?.let { throw UnsupportedCoordinateSystemException(it.toString()) }

            GeometryCollection().apply {
                // Read coordinate reference system from the first polygon. Remove the CRS from
                // all polygons and add it to the GeometryCollection.
                this.crs = areas.first().geometries().first().crs!!
                this.geometries =
                    areas.flatMap { area ->
                        area.geometries().map { polygon ->
                            Polygon(polygon.exteriorRing).apply {
                                this.crs = null
                                polygon.interiorRings.forEach { this.addInteriorRing(it) }
                            }
                        }
                    }
            }
        }
    }

    private fun path(vararg field: String) =
        field.joinToString(separator = ".", prefix = "applicationData.")

    private fun <T> T?.orThrow(path: String) = this ?: throw AlluDataException(path, NULL)

    private fun workDescription(cableReport: CableReportApplicationData): String {
        val excavation = cableReport.rockExcavation.orThrow(path("rockExcavation"))
        return cableReport.workDescription + excavationText(excavation)
    }

    private fun excavationText(excavation: Boolean): String =
        if (excavation) "\nLouhitaan" else "\nEi louhita"
}

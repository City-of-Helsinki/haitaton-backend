package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
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
            val description = workDescription(cableReport = this)
            AlluCableReportApplicationData(
                name = name,
                customerWithContacts =
                    customerWithContacts.toAlluData(path("customerWithContacts")),
                geometry = getGeometry(applicationData = this),
                startTime = startTime.orThrow(path("startTime")),
                endTime = endTime.orThrow(path("endTime")),
                pendingOnClient = pendingOnClient,
                identificationNumber = hankeTunnus,
                clientApplicationKind = description, // intentional
                workDescription = description,
                contractorWithContacts =
                    contractorWithContacts.toAlluData(path("contractorWithContacts")),
                postalAddress = postalAddress?.toAlluData(),
                representativeWithContacts =
                    representativeWithContacts?.toAlluData(path("representativeWithContacts")),
                invoicingCustomer = invoicingCustomer?.toAlluData(path("invoicingCustomer")),
                customerReference = customerReference,
                area = area,
                propertyDeveloperWithContacts =
                    propertyDeveloperWithContacts?.toAlluData(
                        path("propertyDeveloperWithContacts")
                    ),
                constructionWork = constructionWork,
                maintenanceWork = maintenanceWork,
                emergencyWork = emergencyWork,
                propertyConnectivity = propertyConnectivity
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
                .map { it.geometry.crs?.properties?.get("name") }
                .find { it.toString() != COORDINATE_SYSTEM_URN }
                ?.let { throw UnsupportedCoordinateSystemException(it.toString()) }

            GeometryCollection().apply {
                // Read coordinate reference system from the first polygon. Remove the CRS from
                // all polygons and add it to the GeometryCollection.
                this.crs = areas.first().geometry.crs!!
                this.geometries =
                    areas.map { area ->
                        Polygon(area.geometry.exteriorRing).apply {
                            this.crs = null
                            area.geometry.interiorRings.forEach { this.addInteriorRing(it) }
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

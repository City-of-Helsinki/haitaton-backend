package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import fi.hel.haitaton.hanke.geometria.UnsupportedCoordinateSystemException
import org.geojson.GeometryCollection

object ApplicationDataMapper {

    fun toAlluData(applicationData: CableReportApplicationData): AlluCableReportApplicationData =
        AlluCableReportApplicationData(
            applicationData.name,
            applicationData.customerWithContacts.toAlluData("applicationData.customerWithContacts"),
            getGeometry(applicationData),
            applicationData.startTime
                ?: throw AlluDataException("applicationData.startTime", AlluDataError.NULL),
            applicationData.endTime
                ?: throw AlluDataException("applicationData.endTime", AlluDataError.NULL),
            applicationData.pendingOnClient,
            applicationData.identificationNumber,
            applicationData.clientApplicationKind,
            getWorkDescription(applicationData),
            applicationData.contractorWithContacts.toAlluData(
                "applicationData.contractorWithContacts"
            ),
            applicationData.postalAddress?.toAlluData(),
            applicationData.representativeWithContacts?.toAlluData(
                "applicationData.representativeWithContacts"
            ),
            applicationData.invoicingCustomer?.toAlluData("applicationData.invoicingCustomer"),
            applicationData.customerReference,
            applicationData.area,
            applicationData.propertyDeveloperWithContacts?.toAlluData(
                "applicationData.propertyDeveloperWithContacts"
            ),
            applicationData.constructionWork,
            applicationData.maintenanceWork,
            applicationData.emergencyWork,
            applicationData.propertyConnectivity
        )

    private fun getWorkDescription(applicationData: CableReportApplicationData): String {
        val rockExcavation =
            applicationData.rockExcavation
                ?: throw AlluDataException("applicationData.rockExcavation", AlluDataError.NULL)
        return applicationData.workDescription +
            if (rockExcavation) "\nLouhitaan" else "\nEi louhita"
    }

    /**
     * For the switch-over period between geometry and areas, support both areas and geometry. If
     * both are missing, throw an exception.
     */
    internal fun getGeometry(applicationData: ApplicationData): GeometryCollection {
        val areas = applicationData.areas
        return if (!areas.isNullOrEmpty()) {
            // Check that all polygons have the coordinate reference system Haitaton understands
            areas
                .map { it.geometry.crs?.properties?.get("name") }
                .find { it.toString() != COORDINATE_SYSTEM_URN }
                ?.let { throw UnsupportedCoordinateSystemException(it.toString()) }

            GeometryCollection().apply {
                // Read coordinate reference system from the first polygon. Remove the CRS from
                // all polygons and add it to the GeometryCollection.
                this.crs = areas.first().geometry.crs!!
                this.geometries = areas.map { area -> area.geometry.apply { this.crs = null } }
            }
        } else {
            applicationData.geometry
                ?: throw AlluDataException("applicationData.areas", AlluDataError.EMPTY_OR_NULL)
        }
    }
}

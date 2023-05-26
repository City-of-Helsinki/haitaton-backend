package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import fi.hel.haitaton.hanke.application.AlluDataError.EMPTY_OR_NULL
import fi.hel.haitaton.hanke.application.AlluDataError.NULL
import fi.hel.haitaton.hanke.geometria.UnsupportedCoordinateSystemException
import java.time.ZonedDateTime
import org.geojson.GeometryCollection
import org.geojson.Polygon

object ApplicationDataMapper {

    private const val BASE = "applicationData."

    private const val AREAS = "areas"
    private const val CONTRACTOR = "contractorWithContacts"
    private const val CUSTOMER = "customerWithContacts"
    private const val DEVELOPER = "propertyDeveloperWithContacts"
    private const val EXCAVATION = "rockExcavation"
    private const val INVOICING = "invoicingCustomer"
    private const val REPRESENTATIVE = "representativeWithContacts"
    private const val TIME_START = "startTime"
    private const val TIME_END = "endTime"

    fun toAlluData(
        hankeTunnus: String,
        applicationData: CableReportApplicationData
    ): AlluCableReportApplicationData =
        with(applicationData) {
            val description = workDescription(cableReport = this)
            AlluCableReportApplicationData(
                name = name,
                customerWithContacts = customerWithContacts.toAlluData(path(CUSTOMER)),
                geometry = getGeometry(applicationData = this),
                startTime = startTime.orThrow(path(TIME_START)),
                endTime = endTime.orThrow(path(TIME_END)),
                pendingOnClient = pendingOnClient,
                identificationNumber = hankeTunnus,
                clientApplicationKind = description, // intentional
                workDescription = description,
                contractorWithContacts = contractorWithContacts.toAlluData(path(CONTRACTOR)),
                postalAddress = postalAddress?.toAlluData(),
                representativeWithContacts =
                    representativeWithContacts?.toAlluData(path(REPRESENTATIVE)),
                invoicingCustomer = invoicingCustomer?.toAlluData(path(INVOICING)),
                customerReference = customerReference,
                area = area,
                propertyDeveloperWithContacts =
                    propertyDeveloperWithContacts?.toAlluData(path(DEVELOPER)),
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
            throw AlluDataException(path(AREAS), EMPTY_OR_NULL)
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

    private fun path(vararg field: String) = field.joinToString(separator = ".", prefix = BASE)

    private fun ZonedDateTime?.orThrow(path: String) = this ?: throw AlluDataException(path, NULL)

    private fun workDescription(cableReport: CableReportApplicationData): String =
        with(cableReport) {
            val excavation = rockExcavation ?: throw AlluDataException(path(EXCAVATION), NULL)
            return workDescription + excavation.description()
        }

    private fun Boolean.description(): String = if (this) "\nLouhitaan" else "\nEi louhita"
}

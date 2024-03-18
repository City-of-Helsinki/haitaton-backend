package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerWithContacts
import fi.hel.haitaton.hanke.application.AlluDataError.EMPTY_OR_NULL
import fi.hel.haitaton.hanke.application.AlluDataError.NULL
import fi.hel.haitaton.hanke.application.AlluDataException
import fi.hel.haitaton.hanke.geometria.UnsupportedCoordinateSystemException
import org.geojson.GeometryCollection
import org.geojson.Polygon

object HakemusDataMapper {

    fun JohtoselvityshakemusData.toAlluData(hankeTunnus: String): AlluCableReportApplicationData {
        val description = workDescription()
        return AlluCableReportApplicationData(
            name = name,
            customerWithContacts =
                customerWithContacts.orThrow(path("customerWithContacts")).toAlluData(),
            geometry = this.getGeometries(),
            startTime = startTime.orThrow(path("startTime")),
            endTime = endTime.orThrow(path("endTime")),
            pendingOnClient = pendingOnClient,
            identificationNumber = hankeTunnus,
            clientApplicationKind = description, // intentionally copied here
            workDescription = description,
            contractorWithContacts =
                contractorWithContacts.orThrow(path("contractorWithContacts")).toAlluData(),
            postalAddress = postalAddress?.toAlluData(),
            representativeWithContacts = representativeWithContacts?.toAlluData(),
            invoicingCustomer = null,
            customerReference = null,
            area = null,
            propertyDeveloperWithContacts = propertyDeveloperWithContacts?.toAlluData(),
            constructionWork = constructionWork,
            maintenanceWork = maintenanceWork,
            emergencyWork = emergencyWork,
            propertyConnectivity = propertyConnectivity
        )
    }

    /** If areas are missing, throw an exception. */
    private fun HakemusData.getGeometries(): GeometryCollection {
        if (areas.isNullOrEmpty()) {
            throw AlluDataException(path("areas"), EMPTY_OR_NULL)
        } else {
            // Check that all polygons have the coordinate reference system Haitaton understands
            areas!!
                .map { it.geometry.crs?.properties?.get("name") }
                .find { it.toString() != COORDINATE_SYSTEM_URN }
                ?.let { throw UnsupportedCoordinateSystemException(it.toString()) }

            return GeometryCollection().apply {
                // Read coordinate reference system from the first polygon. Remove the CRS from
                // all polygons and add it to the GeometryCollection.
                this.crs = areas!!.first().geometry.crs!!
                this.geometries =
                    areas!!.map { area ->
                        Polygon(area.geometry.exteriorRing).apply {
                            this.crs = null
                            area.geometry.interiorRings.forEach { this.addInteriorRing(it) }
                        }
                    }
            }
        }
    }

    private fun Hakemusyhteystieto.toAlluData(): CustomerWithContacts =
        CustomerWithContacts(
            Customer(
                type = tyyppi,
                name = nimi,
                postalAddress = null,
                country = "FI",
                email = sahkoposti,
                phone = puhelinnumero,
                registryKey = ytunnus,
                ovt = null,
                invoicingOperator = null,
                sapCustomerNumber = null,
            ),
            yhteyshenkilot.map { it.toAlluData() }
        )

    private fun Hakemusyhteyshenkilo.toAlluData() =
        Contact("$etunimi $sukunimi".trim(), sahkoposti, puhelin, tilaaja)

    private fun path(vararg field: String) =
        field.joinToString(separator = ".", prefix = "applicationData.")

    private fun <T> T?.orThrow(path: String) = this ?: throw AlluDataException(path, NULL)

    private fun JohtoselvityshakemusData.workDescription(): String {
        val excavation = rockExcavation.orThrow(path("rockExcavation"))
        return workDescription + excavationText(excavation)
    }

    private fun excavationText(excavation: Boolean): String =
        if (excavation) "\nLouhitaan" else "\nEi louhita"
}

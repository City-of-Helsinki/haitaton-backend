package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import fi.hel.haitaton.hanke.allu.AlluApplicationData
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import fi.hel.haitaton.hanke.allu.AlluExcavationNotificationData
import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerWithContacts
import fi.hel.haitaton.hanke.allu.PostalAddress
import fi.hel.haitaton.hanke.allu.StreetAddress
import fi.hel.haitaton.hanke.geometria.UnsupportedCoordinateSystemException
import fi.hel.haitaton.hanke.hakemus.AlluDataError.EMPTY_OR_NULL
import fi.hel.haitaton.hanke.hakemus.AlluDataError.NULL
import org.geojson.GeometryCollection
import org.geojson.Polygon

object HakemusDataMapper {

    fun HakemusData.toAlluData(hankeTunnus: String): AlluApplicationData =
        when (this) {
            is JohtoselvityshakemusData -> toAlluCableReportData(hankeTunnus)
            is KaivuilmoitusData -> toAlluData(hankeTunnus)
        }

    fun JohtoselvityshakemusData.toAlluCableReportData(
        hankeTunnus: String
    ): AlluCableReportApplicationData {
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

    fun KaivuilmoitusData.toAlluData(hankeTunnus: String): AlluExcavationNotificationData {
        return AlluExcavationNotificationData(
            postalAddress =
                PostalAddress(
                    // TODO: this should be a combination of all area addresses (HAI-1542)
                    streetAddress = StreetAddress(""),
                    postalCode = "",
                    city = ""
                ),
            name = name,
            customerWithContacts =
                customerWithContacts.orThrow(path("customerWithContacts")).toAlluData(),
            representativeWithContacts = representativeWithContacts?.toAlluData(),
            invoicingCustomer = invoicingCustomer?.toAlluData(),
            geometry = getGeometries(),
            startTime = startTime.orThrow(path("startTime")),
            endTime = endTime.orThrow(path("endTime")),
            pendingOnClient = pendingOnClient,
            identificationNumber = hankeTunnus,
            customerReference = invoicingCustomer?.asiakkaanViite,
            area = null, // currently area is not given nor calculated in Haitaton
            clientApplicationKind = workDescription.orThrow(path("workDescription")),
            contractorWithContacts =
                contractorWithContacts.orThrow(path("contractorWithContacts")).toAlluData(),
            propertyDeveloperWithContacts = propertyDeveloperWithContacts?.toAlluData(),
            pksCard = null,
            constructionWork = constructionWork,
            maintenanceWork = maintenanceWork,
            emergencyWork = emergencyWork,
            propertyConnectivity = null,
            workPurpose = workDescription.orThrow(path("workDescription")),
            placementContracts = placementContracts?.toList(),
            cableReports = cableReports?.toList()
        )
    }

    /** If areas are missing, throw an exception. */
    private fun HakemusData.getGeometries(): GeometryCollection {
        if (areas.isNullOrEmpty()) {
            throw AlluDataException(path("areas"), EMPTY_OR_NULL)
        } else {
            // Check that all polygons have the coordinate reference system Haitaton understands
            areas!!
                .flatMap { area -> area.geometries().map { it.crs?.properties?.get("name") } }
                .find { it.toString() != COORDINATE_SYSTEM_URN }
                ?.let { throw UnsupportedCoordinateSystemException(it.toString()) }

            return GeometryCollection().apply {
                // Read coordinate reference system from the first polygon. Remove the CRS from
                // all polygons and add it to the GeometryCollection.
                this.crs = areas!!.first().geometries().first().crs!!
                this.geometries =
                    areas!!.flatMap { area ->
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

    fun Hakemusyhteystieto.toAlluData(): CustomerWithContacts =
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
            yhteyshenkilot.map { it.toAlluContact() }
        )

    fun Hakemusyhteyshenkilo.toAlluContact() =
        Contact("$etunimi $sukunimi".trim(), sahkoposti, puhelin, tilaaja)

    private fun Laskutusyhteystieto.toAlluData(): Customer =
        Customer(
            type = tyyppi,
            name = nimi,
            postalAddress =
                katuosoite?.let {
                    PostalAddress(StreetAddress(it), postinumero ?: "", postitoimipaikka ?: "")
                },
            country = "FI",
            email = sahkoposti,
            phone = puhelinnumero,
            registryKey = ytunnus,
            ovt = ovttunnus,
            invoicingOperator = valittajanTunnus,
            sapCustomerNumber = null,
        )

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

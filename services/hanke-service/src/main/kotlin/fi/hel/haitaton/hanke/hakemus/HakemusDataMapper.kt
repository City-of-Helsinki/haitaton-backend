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
            is KaivuilmoitusData -> toAlluExcavationNotificationData(hankeTunnus)
        }

    fun JohtoselvityshakemusData.toAlluCableReportData(
        hankeTunnus: String
    ): AlluCableReportApplicationData {
        val description = workDescription()
        return AlluCableReportApplicationData(
            identificationNumber = hankeTunnus,
            // We don't send drafts to Allu
            pendingOnClient = false,
            name = name,
            postalAddress = postalAddress?.toAlluData(),
            constructionWork = constructionWork,
            maintenanceWork = maintenanceWork,
            propertyConnectivity = propertyConnectivity,
            emergencyWork = emergencyWork,
            workDescription = description,
            clientApplicationKind = description, // intentionally copied here
            startTime = startTime ?: throw nullException("startTime"),
            endTime = endTime ?: throw nullException("endTime"),
            geometry = getGeometries(),
            area = null,
            customerWithContacts =
                customerWithContacts?.toAlluCustomer()
                    ?: throw nullException("customerWithContacts"),
            contractorWithContacts =
                contractorWithContacts?.toAlluCustomer()
                    ?: throw nullException("contractorWithContacts"),
            propertyDeveloperWithContacts = propertyDeveloperWithContacts?.toAlluCustomer(),
            representativeWithContacts = representativeWithContacts?.toAlluCustomer(),
            invoicingCustomer = null,
            customerReference = null,
            trafficArrangementImages = null,
        )
    }

    fun KaivuilmoitusData.toAlluExcavationNotificationData(
        hankeTunnus: String
    ): AlluExcavationNotificationData =
        AlluExcavationNotificationData(
            identificationNumber = hankeTunnus,
            // We don't send drafts to Allu
            pendingOnClient = false,
            name = name,
            workPurpose = workDescription,
            clientApplicationKind = workDescription,
            constructionWork = constructionWork,
            maintenanceWork = maintenanceWork,
            emergencyWork = emergencyWork,
            cableReports = cableReports?.toList(),
            placementContracts = placementContracts?.toList(),
            startTime = startTime ?: throw nullException("startTime"),
            endTime = endTime ?: throw nullException("endTime"),
            geometry = getGeometries(),
            area = null, // currently area is not given nor calculated in Haitaton
            postalAddress = areas.combinedAddress()?.toAlluData(),
            customerWithContacts =
                customerWithContacts?.toAlluCustomer()
                    ?: throw nullException("customerWithContacts"),
            contractorWithContacts =
                contractorWithContacts?.toAlluCustomer()
                    ?: throw nullException("contractorWithContacts"),
            propertyDeveloperWithContacts = propertyDeveloperWithContacts?.toAlluCustomer(),
            representativeWithContacts = representativeWithContacts?.toAlluCustomer(),
            invoicingCustomer = invoicingCustomer?.toAlluData(),
            customerReference = invoicingCustomer?.asiakkaanViite,
            additionalInfo = additionalInfo,
            pksCard = null,
            selfSupervision = null,
            propertyConnectivity = null,
            trafficArrangements = null,
            trafficArrangementImages = null,
            trafficArrangementImpediment = null,
        )

    /** If areas are missing, throw an exception. */
    internal fun HakemusData.getGeometries(): GeometryCollection =
        areas?.ifEmpty { null }?.let { getGeometries(it) }
            ?: throw AlluDataException(path("areas"), EMPTY_OR_NULL)

    private fun getGeometries(areas: List<Hakemusalue>): GeometryCollection {
        // Check that all polygons have the coordinate reference system Haitaton understands
        areas
            .flatMap { area -> area.geometries().map { it.crs?.properties?.get("name") } }
            .find { it.toString() != COORDINATE_SYSTEM_URN }
            ?.let { throw UnsupportedCoordinateSystemException(it.toString()) }

        return GeometryCollection().apply {
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

    fun Hakemusyhteystieto.toAlluCustomer(): CustomerWithContacts =
        CustomerWithContacts(
            Customer(
                type = tyyppi,
                name = nimi,
                postalAddress = null,
                country = "FI",
                email = sahkoposti,
                phone = puhelinnumero,
                registryKey = registryKey,
                ovt = null,
                invoicingOperator = null,
                sapCustomerNumber = null,
            ),
            yhteyshenkilot.map { it.toAlluContact() },
        )

    fun Hakemusyhteyshenkilo.toAlluContact() =
        Contact("$etunimi $sukunimi".trim(), sahkoposti, puhelin, tilaaja)

    internal fun Laskutusyhteystieto.toAlluData(): Customer =
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
            registryKey = registryKey,
            ovt = ovttunnus,
            invoicingOperator = valittajanTunnus,
            sapCustomerNumber = null,
        )

    private fun path(vararg field: String) =
        field.joinToString(separator = ".", prefix = "applicationData.")

    private fun nullException(path: String) = AlluDataException(path(path), NULL)

    private fun JohtoselvityshakemusData.workDescription(): String {
        val excavation = rockExcavation ?: throw nullException("rockExcavation")
        return workDescription + excavationText(excavation)
    }

    private fun excavationText(excavation: Boolean): String =
        if (excavation) "\nLouhitaan" else "\nEi louhita"
}

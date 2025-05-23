package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.domain.Haittojenhallintasuunnitelma
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.factory.HankealueFactory.DEFAULT_HANKEALUE_ID
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.hakemus.HakemusEntityData
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.hakemus.InvoicingCustomer
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusalue
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusEntityData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusEntityData
import fi.hel.haitaton.hanke.hakemus.PostalAddress
import fi.hel.haitaton.hanke.hakemus.StreetAddress
import fi.hel.haitaton.hanke.hakemus.Tyoalue
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import java.time.ZonedDateTime
import org.geojson.Polygon
import org.springframework.stereotype.Component

const val TEPPO_TESTI = "Teppo Testihenkilö"

@Component
class ApplicationFactory(
    private val hakemusRepository: HakemusRepository,
    private val hankeFactory: HankeFactory,
) {
    companion object {
        const val DEFAULT_APPLICATION_ID: Long = 1
        const val DEFAULT_APPLICATION_NAME: String = "Hakemuksen oletusnimi"
        const val DEFAULT_CABLE_REPORT_APPLICATION_IDENTIFIER: String = "JS2300014"
        const val DEFAULT_EXCAVATION_NOTIFICATION_IDENTIFIER: String = "KP2300015"
        const val DEFAULT_WORK_DESCRIPTION: String = "Työn kuvaus."
        const val DEFAULT_HENKILOTUNNUS: String = "110166-8080"
        const val TEPPO = "Teppo"
        const val TESTIHENKILO = "Testihenkilö"
        const val TEPPO_EMAIL = "teppo@example.test"
        const val TEPPO_PHONE = "04012345678"

        fun createPostalAddress(
            streetAddress: String = "Katu 1",
            postalCode: String = "00100",
            city: String = "Helsinki",
        ) = PostalAddress(StreetAddress(streetAddress), postalCode, city)

        fun createCompanyInvoicingCustomer(
            name: String = "DNA",
            registryKey: String = "3766028-0",
            ovt: String = "003737660280",
        ): InvoicingCustomer =
            InvoicingCustomer(
                type = CustomerType.COMPANY,
                name = name,
                postalAddress = createPostalAddress(),
                email = "info@dna.test",
                phone = "+3581012345678",
                registryKey = registryKey,
                ovt = ovt,
                invoicingOperator = "003721291126",
            )

        fun createPersonInvoicingCustomer(
            name: String = "Liisa Laskutettava",
            registryKey: String = DEFAULT_HENKILOTUNNUS,
        ): InvoicingCustomer =
            InvoicingCustomer(
                type = CustomerType.PERSON,
                name = name,
                postalAddress = createPostalAddress(),
                email = "liisa@laskutus.info",
                phone = "963852741",
                registryKey = registryKey,
                ovt = null,
                invoicingOperator = null,
            )

        fun createCableReportApplicationArea(
            name: String = "Alue",
            geometry: Polygon = GeometriaFactory.secondPolygon(),
        ): JohtoselvitysHakemusalue = JohtoselvitysHakemusalue(name, geometry)

        fun createExcavationNotificationArea(
            name: String = "Alue",
            hankealueId: Int = DEFAULT_HANKEALUE_ID,
            tyoalueet: List<Tyoalue> = listOf(createTyoalue()),
            katuosoite: String = "Katu 1",
            tyonTarkoitukset: Set<TyomaaTyyppi> = setOf(TyomaaTyyppi.VESI),
            meluhaitta: Meluhaitta = Meluhaitta.JATKUVA_MELUHAITTA,
            polyhaitta: Polyhaitta = Polyhaitta.TOISTUVA_POLYHAITTA,
            tarinahaitta: Tarinahaitta = Tarinahaitta.SATUNNAINEN_TARINAHAITTA,
            kaistahaitta: VaikutusAutoliikenteenKaistamaariin =
                VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE,
            kaistahaittojenPituus: AutoliikenteenKaistavaikutustenPituus =
                AutoliikenteenKaistavaikutustenPituus.PITUUS_ALLE_10_METRIA,
            lisatiedot: String = "Lisätiedot",
            haittojenhallintasuunnitelma: Haittojenhallintasuunnitelma = HaittaFactory.DEFAULT_HHS,
        ): KaivuilmoitusAlue =
            KaivuilmoitusAlue(
                name,
                hankealueId,
                tyoalueet,
                katuosoite,
                tyonTarkoitukset,
                meluhaitta,
                polyhaitta,
                tarinahaitta,
                kaistahaitta,
                kaistahaittojenPituus,
                lisatiedot,
                haittojenhallintasuunnitelma,
            )

        fun createTyoalue(
            geometry: Polygon = GeometriaFactory.secondPolygon(),
            area: Double = 100.0,
            tormaystarkasteluTulos: TormaystarkasteluTulos? = null,
        ) = Tyoalue(geometry, area, tormaystarkasteluTulos)

        fun KaivuilmoitusAlue.withHaittojenhallintasuunnitelma(
            haittojenhallintasuunnitelma: Haittojenhallintasuunnitelma =
                HaittaFactory.createHaittojenhallintasuunnitelma()
        ): KaivuilmoitusAlue = copy(haittojenhallintasuunnitelma = haittojenhallintasuunnitelma)

        fun createBlankApplicationData(applicationType: ApplicationType): HakemusEntityData =
            when (applicationType) {
                ApplicationType.CABLE_REPORT -> createBlankCableReportApplicationData()
                ApplicationType.EXCAVATION_NOTIFICATION -> createBlankExcavationNotificationData()
            }

        fun createCableReportApplicationData(
            name: String = DEFAULT_APPLICATION_NAME,
            areas: List<JohtoselvitysHakemusalue>? = listOf(createCableReportApplicationArea()),
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            workDescription: String = DEFAULT_WORK_DESCRIPTION,
            rockExcavation: Boolean = false,
            postalAddress: PostalAddress? = null,
        ): JohtoselvityshakemusEntityData =
            JohtoselvityshakemusEntityData(
                applicationType = ApplicationType.CABLE_REPORT,
                name = name,
                postalAddress = postalAddress,
                rockExcavation = rockExcavation,
                workDescription = workDescription,
                startTime = startTime,
                endTime = endTime,
                areas = areas,
                paperDecisionReceiver = null,
            )

        internal fun createBlankCableReportApplicationData() =
            createCableReportApplicationData(
                name = "",
                areas = null,
                startTime = null,
                endTime = null,
                workDescription = "",
                rockExcavation = false,
                postalAddress = PostalAddress(StreetAddress(""), "", ""),
            )

        fun createExcavationNotificationData(
            name: String = DEFAULT_APPLICATION_NAME,
            workDescription: String = "Työn kuvaus.",
            maintenanceWork: Boolean = false,
            emergencyWork: Boolean = false,
            cableReportDone: Boolean = false,
            rockExcavation: Boolean? = null,
            cableReports: List<String>? = null,
            placementContracts: List<String>? = null,
            requiredCompetence: Boolean = false,
            areas: List<KaivuilmoitusAlue>? = listOf(createExcavationNotificationArea()),
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            invoicingCustomer: InvoicingCustomer? = createCompanyInvoicingCustomer(),
            customerReference: String? = "Asiakkaan viite",
            additionalInfo: String? = null,
        ): KaivuilmoitusEntityData =
            KaivuilmoitusEntityData(
                applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                name = name,
                workDescription = workDescription,
                maintenanceWork = maintenanceWork,
                emergencyWork = emergencyWork,
                cableReportDone = cableReportDone,
                rockExcavation = rockExcavation,
                cableReports = cableReports,
                placementContracts = placementContracts,
                requiredCompetence = requiredCompetence,
                startTime = startTime,
                endTime = endTime,
                areas = areas,
                paperDecisionReceiver = null,
                invoicingCustomer = invoicingCustomer,
                customerReference = customerReference,
                additionalInfo = additionalInfo,
            )

        internal fun createBlankExcavationNotificationData() =
            createExcavationNotificationData(
                name = "",
                workDescription = "",
                rockExcavation = null,
                cableReports = null,
                placementContracts = null,
                areas = null,
                startTime = null,
                endTime = null,
                invoicingCustomer = null,
                customerReference = null,
                additionalInfo = null,
            )

        fun createApplicationEntity(
            id: Long = 3,
            alluid: Int? = null,
            alluStatus: ApplicationStatus? = null,
            applicationIdentifier: String? = null,
            userId: String? = null,
            applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
            hakemusEntityData: HakemusEntityData = createCableReportApplicationData(),
            hanke: HankeEntity,
        ): HakemusEntity =
            HakemusEntity(
                id,
                alluid,
                alluStatus,
                applicationIdentifier,
                userId,
                applicationType,
                hakemusEntityData,
                hanke = hanke,
            )
    }

    /** Save an application to database. it. */
    fun saveApplicationEntity(
        username: String,
        hanke: HankeEntity = hankeFactory.saveMinimal(),
        alluId: Int? = null,
        alluStatus: ApplicationStatus? = null,
        applicationIdentifier: String? = null,
        applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
        hakemusEntityData: HakemusEntityData = createCableReportApplicationData(),
    ): HakemusEntity {
        val hakemusEntity =
            HakemusEntity(
                id = 0,
                alluid = alluId,
                alluStatus = alluStatus,
                applicationIdentifier = applicationIdentifier,
                userId = username,
                applicationType = applicationType,
                hakemusEntityData = hakemusEntityData,
                hanke = hanke,
            )
        return hakemusRepository.save(hakemusEntity)
    }
}

package fi.hel.haitaton.hanke.testdata

import fi.hel.haitaton.hanke.ContactType
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeYhteyshenkiloEntity
import fi.hel.haitaton.hanke.HankeYhteystietoEntity
import fi.hel.haitaton.hanke.HankealueEntity
import fi.hel.haitaton.hanke.HanketunnusService
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.domain.Haittojenhallintasuunnitelma
import fi.hel.haitaton.hanke.domain.Haittojenhallintatyyppi
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.domain.Hankevaihe
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.getCurrentTimeUTCAsLocalTime
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLaskentaService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulosEntity
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import java.time.ZonedDateTime
import kotlin.random.Random
import org.geojson.Feature
import org.geojson.FeatureCollection
import org.geojson.LngLatAlt
import org.geojson.Polygon
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@ConditionalOnProperty(name = ["haitaton.testdata.enabled"], havingValue = "true")
class RandomHankeGenerator(
    private val hankeRepository: HankeRepository,
    private val hanketunnusService: HanketunnusService,
    private val permissionService: PermissionService,
    private val hankekayttajaRepository: HankekayttajaRepository,
    private val geometriatDao: GeometriatDao,
    private val tormaystarkasteluService: TormaystarkasteluLaskentaService,
) {

    // Use the id of your test user (it can be checked from an existing hanke in its
    // 'createdbyuserid' field)
    private val userId = "5f893af3-f7c0-433b-bf50-08f88fbc43a7"

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createRandomHanke(index: Int) {
        val now = ZonedDateTime.now(TZ_UTC)
        val hanke = createRandomHanke(index, now)
        val savedHanke = hankeRepository.save(hanke)

        // Add founder
        val perustaja = createPerustaja(savedHanke)

        // Create random hankealue with geometry
        val hankealue = createRandomHankealue(savedHanke, index)
        savedHanke.alueet.add(hankealue)

        // Create random contacts
        val owner = createRandomOmistaja(savedHanke)
        savedHanke.yhteystiedot.add(owner)
        val contact = createRandomYhteystieto(savedHanke)
        savedHanke.yhteystiedot.add(contact)

        // Create contact persons
        val ownerContactPerson = createYhteyshenkilo(owner, perustaja)
        owner.yhteyshenkilot.add(ownerContactPerson)
        val contactPerson = createYhteyshenkilo(contact, perustaja)
        contact.yhteyshenkilot.add(contactPerson)

        // Save hanke with all relationships
        hankeRepository.save(savedHanke)
    }

    private fun createRandomHanke(index: Int, now: ZonedDateTime): HankeEntity {
        val random = Random(index.toLong())
        val hankeName = "TA-random-hanke-$now-${index + 1}"
        val description = "Random Hanke for stress testing purposes"

        val hanke =
            HankeEntity(
                status = HankeStatus.PUBLIC,
                hankeTunnus = hanketunnusService.newHanketunnus(),
                nimi = hankeName,
                kuvaus = description,
                vaihe = Hankevaihe.RAKENTAMINEN,
                onYKTHanke = random.nextBoolean(),
                version = 0,
                createdByUserId = userId,
                createdAt = getCurrentTimeUTCAsLocalTime(),
                modifiedByUserId = userId,
                modifiedAt = getCurrentTimeUTCAsLocalTime(),
                generated = false,
            )

        hanke.tyomaaKatuosoite = "Testikatu ${index + 1}"
        hanke.tyomaaTyyppi =
            mutableSetOf(TyomaaTyyppi.entries[random.nextInt(TyomaaTyyppi.entries.size)])

        return hanke
    }

    private fun createPerustaja(hanke: HankeEntity): HankekayttajaEntity {
        val permissionEntity =
            permissionService.create(hanke.id, userId, Kayttooikeustaso.KAIKKI_OIKEUDET)
        val kayttaja =
            HankekayttajaEntity(
                hankeId = hanke.id,
                etunimi = "Pertti",
                sukunimi = "Perustaja",
                sahkoposti = "pertti.perustaja@example.com",
                puhelin = "0501234567",
                permission = permissionEntity,
            )
        return hankekayttajaRepository.save(kayttaja)
    }

    private fun createRandomHankealue(hanke: HankeEntity, index: Int): HankealueEntity {
        val random = Random(index.toLong())

        // Create random geometry with Helsinki coordinates
        val geometry = createRandomGeometry(random)
        val geometriat =
            Geometriat(
                featureCollection =
                    FeatureCollection().apply {
                        add(
                            Feature().apply {
                                setGeometry(geometry)
                                properties =
                                    mapOf(
                                        "hankeTunnus" to hanke.hankeTunnus,
                                        "nimi" to "Hankealue 1",
                                    )
                            }
                        )
                    },
                createdByUserId = userId,
                createdAt = ZonedDateTime.now(TZ_UTC),
                modifiedByUserId = null,
                modifiedAt = null,
                version = 0,
            )

        val savedGeometriat = geometriatDao.createGeometriat(geometriat)

        val startDate = ZonedDateTime.now(TZ_UTC).plusDays(random.nextLong(1, 90))
        val endDate = startDate.plusDays(random.nextLong(3, 365))

        val hankealue =
            HankealueEntity(
                hanke = hanke,
                geometriat = savedGeometriat.id,
                haittaAlkuPvm = startDate.toLocalDate(),
                haittaLoppuPvm = endDate.toLocalDate(),
                kaistaHaitta =
                    VaikutusAutoliikenteenKaistamaariin.entries[
                            random.nextInt(VaikutusAutoliikenteenKaistamaariin.entries.size)],
                kaistaPituusHaitta =
                    AutoliikenteenKaistavaikutustenPituus.entries[
                            random.nextInt(AutoliikenteenKaistavaikutustenPituus.entries.size)],
                meluHaitta = Meluhaitta.entries[random.nextInt(Meluhaitta.entries.size)],
                polyHaitta = Polyhaitta.entries[random.nextInt(Polyhaitta.entries.size)],
                tarinaHaitta = Tarinahaitta.entries[random.nextInt(Tarinahaitta.entries.size)],
                nimi = "Hankealue 1",
                tormaystarkasteluTulos = null,
                haittojenhallintasuunnitelma = DEFAULT_HHS.toMutableMap(),
            )

        val tormaystarkasteluTulos =
            tormaystarkasteluService.calculateTormaystarkastelu(hankealue)!!
        hankealue.tormaystarkasteluTulos =
            TormaystarkasteluTulosEntity(
                autoliikenne = tormaystarkasteluTulos.autoliikenne.indeksi,
                haitanKesto = tormaystarkasteluTulos.autoliikenne.haitanKesto,
                katuluokka = tormaystarkasteluTulos.autoliikenne.katuluokka,
                autoliikennemaara = tormaystarkasteluTulos.autoliikenne.liikennemaara,
                kaistahaitta = tormaystarkasteluTulos.autoliikenne.kaistahaitta,
                kaistapituushaitta = tormaystarkasteluTulos.autoliikenne.kaistapituushaitta,
                pyoraliikenne = tormaystarkasteluTulos.pyoraliikenneindeksi,
                linjaautoliikenne = tormaystarkasteluTulos.linjaautoliikenneindeksi,
                raitioliikenne = tormaystarkasteluTulos.raitioliikenneindeksi,
                hankealue = hankealue,
            )

        return hankealue
    }

    private fun createRandomGeometry(random: Random): Polygon {
        // Helsinki city center coordinates (EPSG:3879)
        val helsinkiCenterX = 25496374.16
        val helsinkiCenterY = 6678218.51

        // Random offset within ~2km radius
        val offsetX = random.nextDouble(-2000.0, 2000.0)
        val offsetY = random.nextDouble(-2000.0, 2000.0)

        val centerX = helsinkiCenterX + offsetX
        val centerY = helsinkiCenterY + offsetY

        // Create a rectangular area (5-100 meters)
        val width = random.nextDouble(5.0, 100.0)
        val height = random.nextDouble(5.0, 100.0)

        val coordinates =
            listOf(
                LngLatAlt(centerX - width / 2, centerY - height / 2),
                LngLatAlt(centerX + width / 2, centerY - height / 2),
                LngLatAlt(centerX + width / 2, centerY + height / 2),
                LngLatAlt(centerX - width / 2, centerY + height / 2),
                LngLatAlt(centerX - width / 2, centerY - height / 2), // Close the polygon
            )

        return Polygon().apply { exteriorRing = coordinates }
    }

    private fun createRandomOmistaja(hanke: HankeEntity): HankeYhteystietoEntity {
        val random = Random.Default

        return HankeYhteystietoEntity(
            hanke = hanke,
            contactType = ContactType.OMISTAJA,
            nimi = "Test Yritys ${random.nextInt(1000)}",
            email = "test${random.nextInt(1000)}@example.com",
            puhelinnumero = "050${random.nextInt(1000000, 9999999)}",
            organisaatioNimi = "Test Organisaatio ${random.nextInt(100)}",
            osasto = "Testiosasto",
            rooli = "Projektipäällikkö",
            tyyppi = YhteystietoTyyppi.YRITYS,
            createdByUserId = userId,
            createdAt = getCurrentTimeUTCAsLocalTime(),
            modifiedByUserId = userId,
            modifiedAt = getCurrentTimeUTCAsLocalTime(),
        )
    }

    private fun createRandomYhteystieto(hanke: HankeEntity): HankeYhteystietoEntity {
        val random = Random.Default
        val contactTypes = ContactType.entries - ContactType.OMISTAJA

        return HankeYhteystietoEntity(
            hanke = hanke,
            contactType = contactTypes[random.nextInt(contactTypes.size)],
            nimi = "Test Yritys ${random.nextInt(1000)}",
            email = "test${random.nextInt(1000)}@example.com",
            puhelinnumero = "050${random.nextInt(1000000, 9999999)}",
            organisaatioNimi = "Test Organisaatio ${random.nextInt(100)}",
            osasto = "Testiosasto",
            rooli = "Projektipäällikkö",
            tyyppi = YhteystietoTyyppi.YRITYS,
            createdByUserId = userId,
            createdAt = getCurrentTimeUTCAsLocalTime(),
            modifiedByUserId = userId,
            modifiedAt = getCurrentTimeUTCAsLocalTime(),
        )
    }

    private fun createYhteyshenkilo(
        contact: HankeYhteystietoEntity,
        user: HankekayttajaEntity,
    ): HankeYhteyshenkiloEntity {
        return HankeYhteyshenkiloEntity(hankeKayttaja = user, hankeYhteystieto = contact)
    }

    companion object {
        const val DEFAULT_HHS_YLEINEN = "Yleisten haittojen hallintasuunnitelma"
        const val DEFAULT_HHS_PYORALIIKENNE =
            "Pyöräliikenteelle koituvien haittojen hallintasuunnitelma"
        const val DEFAULT_HHS_AUTOLIIKENNE =
            "Autoliikenteelle koituvien haittojen hallintasuunnitelma"
        const val DEFAULT_HHS_LINJAAUTOLIIKENNE =
            "Linja-autoliikenteelle koituvien haittojen hallintasuunnitelma"
        const val DEFAULT_HHS_RAITIOLIIKENNE =
            "Raitioliikenteelle koituvien haittojen hallintasuunnitelma"
        const val DEFAULT_HHS_MUUT = "Muiden haittojen hallintasuunnitelma"

        val DEFAULT_HHS: Haittojenhallintasuunnitelma =
            mapOf(
                Haittojenhallintatyyppi.YLEINEN to DEFAULT_HHS_YLEINEN,
                Haittojenhallintatyyppi.PYORALIIKENNE to DEFAULT_HHS_PYORALIIKENNE,
                Haittojenhallintatyyppi.AUTOLIIKENNE to DEFAULT_HHS_AUTOLIIKENNE,
                Haittojenhallintatyyppi.LINJAAUTOLIIKENNE to DEFAULT_HHS_LINJAAUTOLIIKENNE,
                Haittojenhallintatyyppi.RAITIOLIIKENNE to DEFAULT_HHS_RAITIOLIIKENNE,
                Haittojenhallintatyyppi.MUUT to DEFAULT_HHS_MUUT,
            )
    }
}

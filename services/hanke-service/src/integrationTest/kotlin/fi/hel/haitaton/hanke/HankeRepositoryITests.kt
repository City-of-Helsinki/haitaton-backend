package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.domain.Hankevaihe
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YRITYS
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
internal class HankeRepositoryITests : DatabaseTest() {

    @Autowired private lateinit var hankeRepository: HankeRepository

    @Test
    @Transactional // due to lazy initialized fields being accessed
    fun `findByHankeTunnus returns existing hanke`() {
        val saved = hankeRepository.save(createBaseHankeEntity("ABC-123"))

        val testResultEntity = hankeRepository.findByHankeTunnus("ABC-123")
        assertThat(testResultEntity?.id).isNotNull
        assertThat(testResultEntity).usingRecursiveComparison().isEqualTo(saved)
    }

    @Test
    fun `findByHankeTunnus does not return anything for non-existing hanke`() {
        createBaseHankeEntity("ABC-123")

        val testResultEntity = hankeRepository.findByHankeTunnus("NOT-000")

        assertThat(testResultEntity).isNull()
    }

    @Test
    fun `basic fields, tyomaa and haitat fields can be round-trip saved and loaded`() {
        val baseHankeEntity = createBaseHankeEntity("ABC-123")
        baseHankeEntity.tyomaaKatuosoite = "katu 1"
        baseHankeEntity.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
        baseHankeEntity.tyomaaTyyppi.add(TyomaaTyyppi.MUU)
        baseHankeEntity.kaistaHaitta =
            VaikutusAutoliikenteenKaistamaariin.VAHENTAA_KAISTAN_YHDELLA_AJOSUUNNALLA
        baseHankeEntity.kaistaPituusHaitta =
            AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA
        baseHankeEntity.meluHaitta = Meluhaitta.SATUNNAINEN_HAITTA
        baseHankeEntity.polyHaitta = Polyhaitta.LYHYTAIKAINEN_TOISTUVA_HAITTA
        baseHankeEntity.tarinaHaitta = Tarinahaitta.PITKAKESTOINEN_TOISTUVA_HAITTA
        hankeRepository.save(baseHankeEntity)

        val loadedHanke = hankeRepository.findByHankeTunnus("ABC-123")

        assertThat(loadedHanke).isNotNull
        assertThat(loadedHanke!!.status).isEqualTo(HankeStatus.DRAFT)
        assertThat(loadedHanke.nimi).isEqualTo("nimi")
        assertThat(loadedHanke.kuvaus).isEqualTo("kuvaus")
        assertThat(loadedHanke.vaihe).isEqualTo(Hankevaihe.SUUNNITTELU)
        assertThat(loadedHanke.tyomaaKatuosoite).isEqualTo("katu 1")
        assertThat(loadedHanke.tyomaaTyyppi).contains(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
        assertThat(loadedHanke.kaistaHaitta)
            .isEqualTo(VaikutusAutoliikenteenKaistamaariin.VAHENTAA_KAISTAN_YHDELLA_AJOSUUNNALLA)
        assertThat(loadedHanke.kaistaPituusHaitta)
            .isEqualTo(AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA)
        assertThat(loadedHanke.meluHaitta).isEqualTo(Meluhaitta.SATUNNAINEN_HAITTA)
        assertThat(loadedHanke.polyHaitta).isEqualTo(Polyhaitta.LYHYTAIKAINEN_TOISTUVA_HAITTA)
        assertThat(loadedHanke.tarinaHaitta).isEqualTo(Tarinahaitta.PITKAKESTOINEN_TOISTUVA_HAITTA)
    }

    @Test
    @Transactional // due to lazy initialized fields being accessed
    fun `yhteystieto fields can be round-trip saved and loaded`() {
        val baseHankeEntity = createBaseHankeEntity("ABC-124")
        val hankeYhteystietoEntity1 = createOmistaja(baseHankeEntity)
        val hankeYhteystietoEntity2 = createRakennuttaja(baseHankeEntity)
        val hankeYhteystietoEntity3 = createToteuttaja(baseHankeEntity)
        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity1)
        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity2)
        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity3)
        hankeRepository.save(baseHankeEntity)

        val loadedHanke = hankeRepository.findByHankeTunnus("ABC-124")

        assertThat(loadedHanke).isNotNull
        assertThat(loadedHanke!!.listOfHankeYhteystieto).isNotNull
        assertThat(loadedHanke.listOfHankeYhteystieto).hasSize(3)
        val loadedHankeYhteystietoEntity1 = loadedHanke.listOfHankeYhteystieto[0]
        val loadedHankeYhteystietoEntity2 = loadedHanke.listOfHankeYhteystieto[1]
        val loadedHankeYhteystietoEntity3 = loadedHanke.listOfHankeYhteystieto[2]
        assertThat(loadedHankeYhteystietoEntity1).isNotNull
        assertThat(loadedHankeYhteystietoEntity1.nimi).isEqualTo("Etu1 Suku1")
        assertThat(loadedHankeYhteystietoEntity1.email).isEqualTo("email1")
        assertThat(loadedHankeYhteystietoEntity1.puhelinnumero).isEqualTo("0101111111")
        assertThat(loadedHankeYhteystietoEntity1.organisaatioNimi).isEqualTo("org1")
        assertThat(loadedHankeYhteystietoEntity1.osasto).isEqualTo("osasto1")
        assertThat(loadedHankeYhteystietoEntity1.createdByUserId).isEqualTo("1")
        assertThat(loadedHankeYhteystietoEntity1.createdAt).isEqualTo(datetime())
        assertThat(loadedHankeYhteystietoEntity1.modifiedByUserId).isEqualTo("11")
        assertThat(loadedHankeYhteystietoEntity1.modifiedAt).isEqualTo(datetime())
        assertThat(loadedHankeYhteystietoEntity1.tyyppi).isEqualTo(YRITYS)
        assertThat(loadedHankeYhteystietoEntity1.yhteyshenkilot)
            .hasSameElementsAs(listOf(createYhteyshenkilo()))
        assertThat(loadedHankeYhteystietoEntity1.hanke).isSameAs(loadedHanke)
        assertThat(loadedHankeYhteystietoEntity2).isNotNull
        assertThat(loadedHankeYhteystietoEntity2.nimi).isEqualTo("Etu2 Suku2")
        assertThat(loadedHankeYhteystietoEntity2.email).isEqualTo("email2")
        assertThat(loadedHankeYhteystietoEntity2.puhelinnumero).isEqualTo("0102222222")
        assertThat(loadedHankeYhteystietoEntity2.organisaatioNimi).isEqualTo("org2")
        assertThat(loadedHankeYhteystietoEntity2.osasto).isEqualTo("osasto2")
        assertThat(loadedHankeYhteystietoEntity2.createdByUserId).isEqualTo("2")
        assertThat(loadedHankeYhteystietoEntity2.createdAt).isEqualTo(datetime())
        assertThat(loadedHankeYhteystietoEntity2.modifiedByUserId).isEqualTo("22")
        assertThat(loadedHankeYhteystietoEntity2.modifiedAt).isEqualTo(datetime())
        assertThat(loadedHankeYhteystietoEntity2.yhteyshenkilot)
            .hasSameElementsAs(listOf(createYhteyshenkilo()))
        assertThat(loadedHankeYhteystietoEntity3).isNotNull
        assertThat(loadedHankeYhteystietoEntity3.nimi).isEqualTo("Etu3 Suku3")
        assertThat(loadedHankeYhteystietoEntity3.email).isEqualTo("email3")
        assertThat(loadedHankeYhteystietoEntity3.puhelinnumero).isEqualTo("0103333333")
        assertThat(loadedHankeYhteystietoEntity3.organisaatioNimi).isEqualTo("org3")
        assertThat(loadedHankeYhteystietoEntity3.osasto).isEqualTo("osasto3")
        assertThat(loadedHankeYhteystietoEntity3.createdByUserId).isEqualTo("3")
        assertThat(loadedHankeYhteystietoEntity3.createdAt).isEqualTo(datetime())
        assertThat(loadedHankeYhteystietoEntity3.modifiedByUserId).isEqualTo("33")
        assertThat(loadedHankeYhteystietoEntity3.modifiedAt).isEqualTo(datetime())
        assertThat(loadedHankeYhteystietoEntity3.yhteyshenkilot)
            .hasSameElementsAs(listOf(createYhteyshenkilo()))
    }

    @Test
    @Transactional // due to lazy initialized fields being accessed
    fun `yhteystieto entry can be round-trip deleted`() {
        val baseHankeEntity = createBaseHankeEntity("ABC-124")
        val hankeYhteystietoEntity1 = createOmistaja(baseHankeEntity)
        val hankeYhteystietoEntity2 = createRakennuttaja(baseHankeEntity)
        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity1)
        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity2)
        hankeRepository.save(baseHankeEntity)

        val loadedHanke = hankeRepository.findByHankeTunnus("ABC-124")

        assertThat(loadedHanke).isNotNull
        assertThat(loadedHanke!!.listOfHankeYhteystieto).isNotNull
        assertThat(loadedHanke.listOfHankeYhteystieto).hasSize(2)
        val loadedHankeYhteystietoEntity1 = loadedHanke.listOfHankeYhteystieto[0]

        loadedHanke.removeYhteystieto(loadedHankeYhteystietoEntity1)

        hankeRepository.save(loadedHanke)

        val loadedHanke2 = hankeRepository.findByHankeTunnus("ABC-124")
        assertThat(loadedHanke2).isNotNull
        assertThat(loadedHanke2!!.listOfHankeYhteystieto).isNotNull
        assertThat(loadedHanke2.listOfHankeYhteystieto).hasSize(1)
    }

    private fun createBaseHankeEntity(hankeTunnus: String) =
        HankeEntity(
            status = HankeStatus.DRAFT,
            hankeTunnus = hankeTunnus,
            nimi = "nimi",
            kuvaus = "kuvaus",
            vaihe = Hankevaihe.SUUNNITTELU,
            onYKTHanke = true,
            version = 1,
            createdByUserId = null,
            createdAt = null,
            modifiedByUserId = null,
            modifiedAt = null,
        )

    /* Keeping just seconds so that database truncation does not affect testing. */
    private fun datetime() = LocalDateTime.of(2020, 2, 20, 20, 20, 20)

    private fun createOmistaja(baseHankeEntity: HankeEntity) =
        HankeYhteystietoEntity(
            contactType = ContactType.OMISTAJA,
            nimi = "Etu1 Suku1",
            email = "email1",
            puhelinnumero = "0101111111",
            organisaatioNimi = "org1",
            osasto = "osasto1",
            dataLocked = false,
            dataLockInfo = null,
            createdByUserId = "1",
            createdAt = datetime(),
            modifiedByUserId = "11",
            modifiedAt = datetime(),
            id = null,
            yhteyshenkilot = listOf(createYhteyshenkilo()),
            tyyppi = YRITYS,
            hanke = baseHankeEntity,
        )

    private fun createRakennuttaja(baseHankeEntity: HankeEntity) =
        HankeYhteystietoEntity(
            contactType = ContactType.RAKENNUTTAJA,
            nimi = "Etu2 Suku2",
            email = "email2",
            puhelinnumero = "0102222222",
            organisaatioNimi = "org2",
            osasto = "osasto2",
            dataLocked = false,
            dataLockInfo = null,
            createdByUserId = "2",
            createdAt = datetime(),
            modifiedByUserId = "22",
            modifiedAt = datetime(),
            id = null,
            yhteyshenkilot = listOf(createYhteyshenkilo()),
            tyyppi = YRITYS,
            hanke = baseHankeEntity,
        )

    private fun createToteuttaja(baseHankeEntity: HankeEntity) =
        HankeYhteystietoEntity(
            contactType = ContactType.TOTEUTTAJA,
            nimi = "Etu3 Suku3",
            email = "email3",
            puhelinnumero = "0103333333",
            organisaatioNimi = "org3",
            osasto = "osasto3",
            dataLocked = false,
            dataLockInfo = null,
            createdByUserId = "3",
            createdAt = datetime(),
            modifiedByUserId = "33",
            modifiedAt = datetime(),
            id = null,
            yhteyshenkilot = listOf(createYhteyshenkilo()),
            tyyppi = YRITYS,
            hanke = baseHankeEntity,
        )

    private fun createYhteyshenkilo() =
        Yhteyshenkilo("Ali", "Kontakti", "ali.kontakti@testi.com", "050-3785641")
}

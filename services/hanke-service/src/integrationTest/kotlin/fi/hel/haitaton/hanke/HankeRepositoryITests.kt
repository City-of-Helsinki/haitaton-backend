package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YRITYS
import java.time.LocalDateTime
import javax.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
internal class HankeRepositoryITests : DatabaseTest() {

    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var hankeRepository: HankeRepository

    @Test
    @Transactional
    fun `findByHankeTunnus returns existing hanke`() {
        // First insert one hanke to the repository (using entityManager directly):
        val hankeEntity = createBaseHankeEntity("ABC-123")
        entityManager.persist(hankeEntity)
        entityManager.flush()

        // Try to find that (using our Repository implementation):
        val testResultEntity = hankeRepository.findByHankeTunnus("ABC-123")
        assertThat(testResultEntity).isEqualTo(hankeEntity)
    }

    @Test
    @Transactional
    fun `findByHankeTunnus does not return anything for non-existing hanke`() {
        // First insert one hanke to the repository (using entityManager directly):
        val hankeEntity = createBaseHankeEntity("ABC-123")
        entityManager.persist(hankeEntity)
        entityManager.flush()

        // Check that non-existing hanke returns nothing:
        val testResultEntity = hankeRepository.findByHankeTunnus("NOT-000")
        assertThat(testResultEntity).isNull()
    }

    // Purpose of these field tests is to ensure that entity mappings and liquibase columns work
    // correctly.
    @Test
    @Transactional
    fun `basic fields, tyomaa and haitat fields can be round-trip saved and loaded`() {
        val datetime = LocalDateTime.of(2020, 2, 20, 20, 20)
        val date = datetime.toLocalDate()
        // Setup test fields
        val baseHankeEntity = createBaseHankeEntity("ABC-123")
        baseHankeEntity.tyomaaKatuosoite = "katu 1"
        baseHankeEntity.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
        baseHankeEntity.tyomaaTyyppi.add(TyomaaTyyppi.MUU)
        baseHankeEntity.haittaAlkuPvm = date
        baseHankeEntity.haittaLoppuPvm = date
        baseHankeEntity.kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI
        baseHankeEntity.kaistaPituusHaitta = KaistajarjestelynPituus.KOLME
        baseHankeEntity.meluHaitta = Haitta13.YKSI
        baseHankeEntity.polyHaitta = Haitta13.KAKSI
        baseHankeEntity.tarinaHaitta = Haitta13.KOLME

        // Save it
        hankeRepository.save(baseHankeEntity)
        // Make sure the stuff is run to database (though not necessarily committed)
        entityManager.flush()
        // Ensure the original entity is no longer in Hibernate's 1st level cache
        entityManager.clear()

        // Load it back to different entity and check the fields
        val loadedHanke = hankeRepository.findByHankeTunnus("ABC-123")
        assertThat(loadedHanke).isNotNull

        assertThat(loadedHanke!!.status).isEqualTo(HankeStatus.DRAFT)
        assertThat(loadedHanke.nimi).isEqualTo("nimi")
        assertThat(loadedHanke.kuvaus).isEqualTo("kuvaus")
        assertThat(loadedHanke.alkuPvm).isEqualTo(date)
        assertThat(loadedHanke.loppuPvm).isEqualTo(date)
        assertThat(loadedHanke.vaihe).isEqualTo(Vaihe.SUUNNITTELU)
        assertThat(loadedHanke.suunnitteluVaihe).isEqualTo(SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS)

        assertThat(loadedHanke.tyomaaKatuosoite).isEqualTo("katu 1")
        assertThat(loadedHanke.tyomaaTyyppi).contains(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
        assertThat(loadedHanke.haittaAlkuPvm).isEqualTo(date)
        assertThat(loadedHanke.haittaLoppuPvm).isEqualTo(date)
        assertThat(loadedHanke.kaistaHaitta)
            .isEqualTo(TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI)
        assertThat(loadedHanke.kaistaPituusHaitta).isEqualTo(KaistajarjestelynPituus.KOLME)
        assertThat(loadedHanke.meluHaitta).isEqualTo(Haitta13.YKSI)
        assertThat(loadedHanke.polyHaitta).isEqualTo(Haitta13.KAKSI)
        assertThat(loadedHanke.tarinaHaitta).isEqualTo(Haitta13.KOLME)
    }

    @Test
    @Transactional
    fun `yhteystieto fields can be round-trip saved and loaded`() {
        // Setup test fields
        val baseHankeEntity = createBaseHankeEntity("ABC-124")

        // Note, leaving id and hanke fields unset on purpose (Hibernate should set them as needed)
        val hankeYhteystietoEntity1 = createOmistaja(baseHankeEntity)
        val hankeYhteystietoEntity2 = createRakennuttaja(baseHankeEntity)
        val hankeYhteystietoEntity3 = createToteuttaja(baseHankeEntity)

        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity1)
        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity2)
        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity3)

        // Save it:
        hankeRepository.save(baseHankeEntity)
        // Make sure the stuff is run to database (though not necessarily committed)
        entityManager.flush()
        // Ensure the original entity is no longer in Hibernate's 1st level cache
        entityManager.clear()

        // Load it back to different entity and check the fields
        val loadedHanke = hankeRepository.findByHankeTunnus("ABC-124")
        assertThat(loadedHanke).isNotNull
        assertThat(loadedHanke!!.listOfHankeYhteystieto).isNotNull
        assertThat(loadedHanke.listOfHankeYhteystieto).hasSize(3)

        val loadedHankeYhteystietoEntity1 = loadedHanke.listOfHankeYhteystieto[0]
        val loadedHankeYhteystietoEntity2 = loadedHanke.listOfHankeYhteystieto[1]
        val loadedHankeYhteystietoEntity3 = loadedHanke.listOfHankeYhteystieto[2]

        // Check every field from one yhteystieto:
        assertThat(loadedHankeYhteystietoEntity1).isNotNull
        assertThat(loadedHankeYhteystietoEntity1.nimi).isEqualTo("Etu1 Suku1")
        assertThat(loadedHankeYhteystietoEntity1.email).isEqualTo("email1")
        assertThat(loadedHankeYhteystietoEntity1.puhelinnumero).isEqualTo("0101111111")
        assertThat(loadedHankeYhteystietoEntity1.organisaatioId).isEqualTo(1)
        assertThat(loadedHankeYhteystietoEntity1.organisaatioNimi).isEqualTo("org1")
        assertThat(loadedHankeYhteystietoEntity1.osasto).isEqualTo("osasto1")
        assertThat(loadedHankeYhteystietoEntity1.createdByUserId).isEqualTo("1")
        assertThat(loadedHankeYhteystietoEntity1.createdAt).isEqualTo(datetime())
        assertThat(loadedHankeYhteystietoEntity1.modifiedByUserId).isEqualTo("11")
        assertThat(loadedHankeYhteystietoEntity1.modifiedAt).isEqualTo(datetime())
        assertThat(loadedHankeYhteystietoEntity1.alikontaktit)
            .hasSameElementsAs(listOf(createAlikontakti()))
        // Check the back reference to parent Hanke:
        assertThat(loadedHankeYhteystietoEntity1.hanke).isSameAs(loadedHanke)
        // Check other yhteystietos:
        assertThat(loadedHankeYhteystietoEntity2).isNotNull
        assertThat(loadedHankeYhteystietoEntity2.nimi).isEqualTo("Etu2 Suku2")
        assertThat(loadedHankeYhteystietoEntity2.email).isEqualTo("email2")
        assertThat(loadedHankeYhteystietoEntity2.puhelinnumero).isEqualTo("0102222222")
        assertThat(loadedHankeYhteystietoEntity2.organisaatioId).isEqualTo(2)
        assertThat(loadedHankeYhteystietoEntity2.organisaatioNimi).isEqualTo("org2")
        assertThat(loadedHankeYhteystietoEntity2.osasto).isEqualTo("osasto2")
        assertThat(loadedHankeYhteystietoEntity2.createdByUserId).isEqualTo("2")
        assertThat(loadedHankeYhteystietoEntity2.createdAt).isEqualTo(datetime())
        assertThat(loadedHankeYhteystietoEntity2.modifiedByUserId).isEqualTo("22")
        assertThat(loadedHankeYhteystietoEntity2.modifiedAt).isEqualTo(datetime())
        assertThat(loadedHankeYhteystietoEntity2.alikontaktit)
            .hasSameElementsAs(listOf(createAlikontakti()))

        assertThat(loadedHankeYhteystietoEntity3).isNotNull
        assertThat(loadedHankeYhteystietoEntity3.nimi).isEqualTo("Etu3 Suku3")
        assertThat(loadedHankeYhteystietoEntity3.email).isEqualTo("email3")
        assertThat(loadedHankeYhteystietoEntity3.puhelinnumero).isEqualTo("0103333333")
        assertThat(loadedHankeYhteystietoEntity3.organisaatioId).isEqualTo(3)
        assertThat(loadedHankeYhteystietoEntity3.organisaatioNimi).isEqualTo("org3")
        assertThat(loadedHankeYhteystietoEntity3.osasto).isEqualTo("osasto3")
        assertThat(loadedHankeYhteystietoEntity3.createdByUserId).isEqualTo("3")
        assertThat(loadedHankeYhteystietoEntity3.createdAt).isEqualTo(datetime())
        assertThat(loadedHankeYhteystietoEntity3.modifiedByUserId).isEqualTo("33")
        assertThat(loadedHankeYhteystietoEntity3.modifiedAt).isEqualTo(datetime())
        assertThat(loadedHankeYhteystietoEntity3.alikontaktit)
            .hasSameElementsAs(listOf(createAlikontakti()))
    }

    @Test
    @Transactional
    fun `yhteystieto entry can be round-trip deleted`() {
        // Setup test fields
        val baseHankeEntity = createBaseHankeEntity("ABC-124")

        // Note, leaving id and hanke fields unset on purpose (Hibernate should set them as needed)
        val hankeYhteystietoEntity1 = createOmistaja(baseHankeEntity)
        val hankeYhteystietoEntity2 = createRakennuttaja(baseHankeEntity)

        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity1)
        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity2)

        // Save it:
        hankeRepository.save(baseHankeEntity)
        // Make sure the stuff is run to database (though not necessarily committed)
        entityManager.flush()
        // Ensure the original entity is no longer in Hibernate's 1st level cache
        entityManager.clear()

        // Load it back to different entity and check there is two yhteystietos:
        val loadedHanke = hankeRepository.findByHankeTunnus("ABC-124")
        assertThat(loadedHanke).isNotNull
        assertThat(loadedHanke!!.listOfHankeYhteystieto).isNotNull
        assertThat(loadedHanke.listOfHankeYhteystieto).hasSize(2)

        // Remove the first yhteystieto, and save again; (also record the other one's organisaatioId
        // for later confirmation):
        val loadedHankeYhteystietoEntity1 = loadedHanke.listOfHankeYhteystieto[0]
        val loadedHankeYhteystietoEntity2 = loadedHanke.listOfHankeYhteystieto[1]
        val loadedHankeYhteystietoOrgId2 = loadedHankeYhteystietoEntity2.organisaatioId

        loadedHanke.removeYhteystieto(loadedHankeYhteystietoEntity1)

        hankeRepository.save(loadedHanke)
        entityManager
            .flush() // Make sure the stuff is run to database (though not necessarily committed)
        entityManager
            .clear() // Ensure the original entity is no longer in Hibernate's 1st level cache

        // Reload the hanke and check that only the second hanke remains:
        val loadedHanke2 = hankeRepository.findByHankeTunnus("ABC-124")
        assertThat(loadedHanke2).isNotNull
        assertThat(loadedHanke2!!.listOfHankeYhteystieto).isNotNull
        assertThat(loadedHanke2.listOfHankeYhteystieto).hasSize(1)
        assertThat(loadedHanke2.listOfHankeYhteystieto[0].organisaatioId)
            .isEqualTo(loadedHankeYhteystietoOrgId2)
    }

    private fun createBaseHankeEntity(hankeTunnus: String) =
        HankeEntity(
            status = HankeStatus.DRAFT,
            hankeTunnus = hankeTunnus,
            nimi = "nimi",
            kuvaus = "kuvaus",
            alkuPvm = datetime().toLocalDate(),
            loppuPvm = datetime().toLocalDate(),
            vaihe = Vaihe.SUUNNITTELU,
            suunnitteluVaihe = SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS,
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
            organisaatioId = 1,
            organisaatioNimi = "org1",
            osasto = "osasto1",
            dataLocked = false,
            dataLockInfo = null,
            createdByUserId = "1",
            createdAt = datetime(),
            modifiedByUserId = "11",
            modifiedAt = datetime(),
            id = null,
            alikontaktit = listOf(createAlikontakti()),
            tyyppi = YRITYS,
            hanke = baseHankeEntity,
        )

    private fun createRakennuttaja(baseHankeEntity: HankeEntity) =
        HankeYhteystietoEntity(
            contactType = ContactType.RAKENNUTTAJA,
            nimi = "Etu2 Suku2",
            email = "email2",
            puhelinnumero = "0102222222",
            organisaatioId = 2,
            organisaatioNimi = "org2",
            osasto = "osasto2",
            dataLocked = false,
            dataLockInfo = null,
            createdByUserId = "2",
            createdAt = datetime(),
            modifiedByUserId = "22",
            modifiedAt = datetime(),
            id = null,
            alikontaktit = listOf(createAlikontakti()),
            tyyppi = YRITYS,
            hanke = baseHankeEntity,
        )

    private fun createToteuttaja(baseHankeEntity: HankeEntity) =
        HankeYhteystietoEntity(
            contactType = ContactType.TOTEUTTAJA,
            nimi = "Etu3 Suku3",
            email = "email3",
            puhelinnumero = "0103333333",
            organisaatioId = 3,
            organisaatioNimi = "org3",
            osasto = "osasto3",
            dataLocked = false,
            dataLockInfo = null,
            createdByUserId = "3",
            createdAt = datetime(),
            modifiedByUserId = "33",
            modifiedAt = datetime(),
            id = null,
            alikontaktit = listOf(createAlikontakti()),
            tyyppi = YRITYS,
            hanke = baseHankeEntity,
        )

    private fun createAlikontakti() =
        Alikontakti("Ali", "Kontakti", "ali.kontakti@testi.com", "050-3785641")
}

package fi.hel.haitaton.hanke

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import java.time.LocalDateTime

/**
 * Testing the HankeRepository with a database.
 */
@DataJpaTest(properties = ["spring.liquibase.enabled=false"])
class HankeRepositoryITests @Autowired constructor(
        val entityManager: TestEntityManager,
        val hankeRepository: HankeRepository) {

    @Test
    fun `findByHankeTunnus returns existing hanke`() {
        // First insert one hanke to the repository (using entityManager directly):
        val hankeEntity = HankeEntity(SaveType.AUTO, "ABC-123", null, null,
                null, null, null, null, false,
                1, null, null, null, null)
        entityManager.persist(hankeEntity)
        entityManager.flush()

        // Try to find that (using our Repository implementation):
        val testResultEntity = hankeRepository.findByHankeTunnus("ABC-123")
        assertThat(testResultEntity).isEqualTo(hankeEntity)
    }

    @Test
    fun `findByHankeTunnus does not return anything for non-existing hanke`() {
        // First insert one hanke to the repository (using entityManager directly):
        val hankeEntity = HankeEntity(SaveType.AUTO, "ABC-123", null, null,
                null, null, null, null, false,
                1, null, null, null, null)
        entityManager.persist(hankeEntity)
        entityManager.flush()

        // Check that non-existing hanke returns nothing:
        val testResultEntity = hankeRepository.findByHankeTunnus("NOT-000")
        assertThat(testResultEntity).isNull()
    }

    // Purpose of these field tests is to ensure that entity mappings and liquibase columns work correctly.
    // Note that due to using DataJpaTest, liquibase may map the field types slightly differently here than
    // in production mode, but these are quite usual field types, so shouldn't be an issue.
    @Test
    fun `basic fields, tyomaa and haitat fields can be round-trip saved and loaded`() {
        val datetime = LocalDateTime.of(2020, 2, 20, 20, 20)
        val date = datetime.toLocalDate()
        // Setup test fields
        val baseHankeEntity = HankeEntity(SaveType.DRAFT, "ABC-123", "nimi", "kuvaus",
                date, date, Vaihe.SUUNNITTELU, SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS, true,
                1, null, null, null, null)
        baseHankeEntity.tyomaaKatuosoite = "katu 1"
        baseHankeEntity.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
        baseHankeEntity.tyomaaTyyppi.add(TyomaaTyyppi.MUU)
        baseHankeEntity.tyomaaKoko = TyomaaKoko.LAAJA_TAI_USEA_KORTTELI
        baseHankeEntity.haittaAlkuPvm = date
        baseHankeEntity.haittaLoppuPvm = date
        baseHankeEntity.kaistaHaitta = Haitta04.KAKSI
        baseHankeEntity.kaistaPituusHaitta = Haitta04.KOLME
        baseHankeEntity.meluHaitta = Haitta13.YKSI
        baseHankeEntity.polyHaitta = Haitta13.KAKSI
        baseHankeEntity.tarinaHaitta = Haitta13.KOLME

        // Save it
        hankeRepository.save(baseHankeEntity)
        entityManager.flush() // Make sure the stuff is run to database (though not necessarily committed)
        entityManager.clear() // Ensure the original entity is no longer in Hibernate's 1st level cache

        // Load it back to different entity and check the fields
        val loadedHanke = hankeRepository.findByHankeTunnus("ABC-123")
        assertThat(loadedHanke).isNotNull

        assertThat(loadedHanke!!.saveType).isEqualTo(SaveType.DRAFT)
        assertThat(loadedHanke.nimi).isEqualTo("nimi")
        assertThat(loadedHanke.kuvaus).isEqualTo("kuvaus")
        assertThat(loadedHanke.alkuPvm).isEqualTo(date)
        assertThat(loadedHanke.loppuPvm).isEqualTo(date)
        assertThat(loadedHanke.vaihe).isEqualTo(Vaihe.SUUNNITTELU)
        assertThat(loadedHanke.suunnitteluVaihe).isEqualTo(SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS)

        assertThat(loadedHanke.tyomaaKatuosoite).isEqualTo("katu 1")
        assertThat(loadedHanke.tyomaaTyyppi).contains(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
        assertThat(loadedHanke.tyomaaKoko).isEqualTo(TyomaaKoko.LAAJA_TAI_USEA_KORTTELI)
        assertThat(loadedHanke.haittaAlkuPvm).isEqualTo(date)
        assertThat(loadedHanke.haittaLoppuPvm).isEqualTo(date)
        assertThat(loadedHanke.kaistaHaitta).isEqualTo(Haitta04.KAKSI)
        assertThat(loadedHanke.kaistaPituusHaitta).isEqualTo(Haitta04.KOLME)
        assertThat(loadedHanke.meluHaitta).isEqualTo(Haitta13.YKSI)
        assertThat(loadedHanke.polyHaitta).isEqualTo(Haitta13.KAKSI)
        assertThat(loadedHanke.tarinaHaitta).isEqualTo(Haitta13.KOLME)
    }

    @Test
    fun `yhteystieto fields can be round-trip saved and loaded`() {
        // Keeping just seconds so that database truncation does not affect testing
        val datetime = LocalDateTime.of(2020, 2, 20, 20, 20, 20)
        val date = datetime.toLocalDate()
        // Setup test fields
        val baseHankeEntity = HankeEntity(SaveType.DRAFT, "ABC-124", "nimi", "kuvaus",
                date, date, Vaihe.SUUNNITTELU, SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS, true,
                1, null, null, null, null)

        // Note, leaving id and hanke fields unset on purpose (Hibernate should set them as needed)
        val hankeYhteystietoEntity1 = HankeYhteystietoEntity(
                ContactType.OMISTAJA, "Suku1", "Etu1", "email1", "0101111111",
                1, "org1", "osasto1",
                "1", datetime, "11", datetime, null, baseHankeEntity)
        val hankeYhteystietoEntity2 = HankeYhteystietoEntity(
                ContactType.ARVIOIJA, "Suku2", "Etu2", "email2", "0102222222",
                2, "org2", "osasto2",
                "2", datetime, "22", datetime, null, baseHankeEntity)
        val hankeYhteystietoEntity3 = HankeYhteystietoEntity(
                ContactType.TOTEUTTAJA, "Suku3", "Etu3", "email3", "0103333333",
                3, "org3", "osasto3",
                "3", datetime, "33", datetime, null, baseHankeEntity)

        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity1)
        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity2)
        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity3)

        // Save it:
        hankeRepository.save(baseHankeEntity)
        entityManager.flush() // Make sure the stuff is run to database (though not necessarily committed)
        entityManager.clear() // Ensure the original entity is no longer in Hibernate's 1st level cache

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
        assertThat(loadedHankeYhteystietoEntity1.sukunimi).isEqualTo("Suku1")
        assertThat(loadedHankeYhteystietoEntity1.etunimi).isEqualTo("Etu1")
        assertThat(loadedHankeYhteystietoEntity1.email).isEqualTo("email1")
        assertThat(loadedHankeYhteystietoEntity1.puhelinnumero).isEqualTo("0101111111")
        assertThat(loadedHankeYhteystietoEntity1.organisaatioId).isEqualTo(1)
        assertThat(loadedHankeYhteystietoEntity1.organisaatioNimi).isEqualTo("org1")
        assertThat(loadedHankeYhteystietoEntity1.osasto).isEqualTo("osasto1")
        assertThat(loadedHankeYhteystietoEntity1.createdByUserId).isEqualTo("1")
        assertThat(loadedHankeYhteystietoEntity1.createdAt).isEqualTo(datetime)
        assertThat(loadedHankeYhteystietoEntity1.modifiedByUserId).isEqualTo("11")
        assertThat(loadedHankeYhteystietoEntity1.modifiedAt).isEqualTo(datetime)
        // Check the back reference to parent Hanke:
        assertThat(loadedHankeYhteystietoEntity1.hanke).isSameAs(loadedHanke)
        // Check other yhteystietos:
        assertThat(loadedHankeYhteystietoEntity2).isNotNull
        assertThat(loadedHankeYhteystietoEntity2.sukunimi).isEqualTo("Suku2")
        assertThat(loadedHankeYhteystietoEntity2.etunimi).isEqualTo("Etu2")
        assertThat(loadedHankeYhteystietoEntity2.email).isEqualTo("email2")
        assertThat(loadedHankeYhteystietoEntity2.puhelinnumero).isEqualTo("0102222222")
        assertThat(loadedHankeYhteystietoEntity2.organisaatioId).isEqualTo(2)
        assertThat(loadedHankeYhteystietoEntity2.organisaatioNimi).isEqualTo("org2")
        assertThat(loadedHankeYhteystietoEntity2.osasto).isEqualTo("osasto2")
        assertThat(loadedHankeYhteystietoEntity2.createdByUserId).isEqualTo("2")
        assertThat(loadedHankeYhteystietoEntity2.createdAt).isEqualTo(datetime)
        assertThat(loadedHankeYhteystietoEntity2.modifiedByUserId).isEqualTo("22")
        assertThat(loadedHankeYhteystietoEntity2.modifiedAt).isEqualTo(datetime)

        assertThat(loadedHankeYhteystietoEntity3).isNotNull
        assertThat(loadedHankeYhteystietoEntity3.sukunimi).isEqualTo("Suku3")
        assertThat(loadedHankeYhteystietoEntity3.etunimi).isEqualTo("Etu3")
        assertThat(loadedHankeYhteystietoEntity3.email).isEqualTo("email3")
        assertThat(loadedHankeYhteystietoEntity3.puhelinnumero).isEqualTo("0103333333")
        assertThat(loadedHankeYhteystietoEntity3.organisaatioId).isEqualTo(3)
        assertThat(loadedHankeYhteystietoEntity3.organisaatioNimi).isEqualTo("org3")
        assertThat(loadedHankeYhteystietoEntity3.osasto).isEqualTo("osasto3")
        assertThat(loadedHankeYhteystietoEntity3.createdByUserId).isEqualTo("3")
        assertThat(loadedHankeYhteystietoEntity3.createdAt).isEqualTo(datetime)
        assertThat(loadedHankeYhteystietoEntity3.modifiedByUserId).isEqualTo("33")
        assertThat(loadedHankeYhteystietoEntity3.modifiedAt).isEqualTo(datetime)
    }

    @Test
    fun `yhteystieto entry can be round-trip deleted`() {
        val datetime = LocalDateTime.of(2020, 2, 20, 20, 20, 20)
        val date = datetime.toLocalDate()
        // Setup test fields
        val baseHankeEntity = HankeEntity(SaveType.DRAFT, "ABC-124", "nimi", "kuvaus",
                date, date, Vaihe.SUUNNITTELU, SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS, true,
                1, null, null, null, null)

        // Note, leaving id and hanke fields unset on purpose (Hibernate should set them as needed)
        val hankeYhteystietoEntity1 = HankeYhteystietoEntity(
                ContactType.OMISTAJA, "Suku1", "Etu1", "email1", "0101111111",
                1, "org1", "osasto1",
                "1", datetime, "11", datetime, null, baseHankeEntity)
        val hankeYhteystietoEntity2 = HankeYhteystietoEntity(
                ContactType.ARVIOIJA, "Suku2", "Etu2", "email2", "0102222222",
                2, "org2", "osasto2",
                "2", datetime, "22", datetime, null, baseHankeEntity)

        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity1)
        baseHankeEntity.addYhteystieto(hankeYhteystietoEntity2)

        // Save it:
        hankeRepository.save(baseHankeEntity)
        entityManager.flush() // Make sure the stuff is run to database (though not necessarily committed)
        entityManager.clear() // Ensure the original entity is no longer in Hibernate's 1st level cache

        // Load it back to different entity and check there is two yhteystietos:
        val loadedHanke = hankeRepository.findByHankeTunnus("ABC-124")
        assertThat(loadedHanke).isNotNull
        assertThat(loadedHanke!!.listOfHankeYhteystieto).isNotNull
        assertThat(loadedHanke.listOfHankeYhteystieto).hasSize(2)

        // Remove the first yhteystieto, and save again; (also record the other one's organisaatioId for later confirmation):
        val loadedHankeYhteystietoEntity1 = loadedHanke.listOfHankeYhteystieto[0]
        val loadedHankeYhteystietoEntity2 = loadedHanke.listOfHankeYhteystieto[1]
        val loadedHankeYhteystietoOrgId2 = loadedHankeYhteystietoEntity2.organisaatioId

        loadedHanke.removeYhteystieto(loadedHankeYhteystietoEntity1)

        hankeRepository.save(loadedHanke)
        entityManager.flush() // Make sure the stuff is run to database (though not necessarily committed)
        entityManager.clear() // Ensure the original entity is no longer in Hibernate's 1st level cache

        // Reload the hanke and check that only the second hanke remains:
        val loadedHanke2 = hankeRepository.findByHankeTunnus("ABC-124")
        assertThat(loadedHanke2).isNotNull
        assertThat(loadedHanke2!!.listOfHankeYhteystieto).isNotNull
        assertThat(loadedHanke2.listOfHankeYhteystieto).hasSize(1)
        assertThat(loadedHanke2.listOfHankeYhteystieto[0].organisaatioId).isEqualTo(loadedHankeYhteystietoOrgId2)
    }


    // TODO: more tests (when more functions appear)








}

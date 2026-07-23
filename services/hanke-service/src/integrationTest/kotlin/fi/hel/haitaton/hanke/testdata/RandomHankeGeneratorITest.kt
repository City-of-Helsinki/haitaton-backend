package fi.hel.haitaton.hanke.testdata

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class RandomHankeGeneratorITest : IntegrationTest() {

    @Autowired private lateinit var randomHankeGenerator: RandomHankeGenerator
    @Autowired private lateinit var hankeRepository: HankeRepository
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var hankekayttajaRepository: HankekayttajaRepository

    @Test
    fun `creates a random Hanke with basic required fields`() {
        val countBefore = hankeRepository.count()

        randomHankeGenerator.createRandomHanke(0)

        assertThat(hankeRepository.count()).isEqualTo(countBefore + 1)
        val hanke = hankeService.loadPublicHanke().single()

        assertThat(hanke).all {
            prop(Hanke::hankeTunnus).isNotNull()
            prop(Hanke::nimi).isNotNull()
            prop(Hanke::status).isEqualTo(HankeStatus.PUBLIC)
            prop(Hanke::generated).isFalse()
            prop(Hanke::createdBy).isNotNull()
            prop(Hanke::createdAt).isNotNull()
        }
    }

    @Test
    fun `creates Hanke with unique hankeTunnus for different indices`() {
        randomHankeGenerator.createRandomHanke(0)
        randomHankeGenerator.createRandomHanke(1)
        randomHankeGenerator.createRandomHanke(2)

        val allHanke = hankeService.loadPublicHanke()
        val hankeTunnukset = allHanke.map { it.hankeTunnus }.toSet()

        assertThat(allHanke).hasSize(3)
        assertThat(hankeTunnukset).hasSize(3) // All unique
    }

    @Test
    fun `creates Hanke with hankealue containing geometries`() {
        randomHankeGenerator.createRandomHanke(0)

        val hanke = hankeService.loadPublicHanke().single()

        assertThat(hanke.alueet).hasSize(1)
        val hankealue = hanke.alueet.first()
        assertThat(hankealue.geometriat).isNotNull()
        assertThat(hankealue.nimi).isNotNull()
        assertThat(hankealue.haittaAlkuPvm).isNotNull()
        assertThat(hankealue.haittaLoppuPvm).isNotNull()
    }

    @Test
    fun `creates Hanke with contact information populated`() {
        randomHankeGenerator.createRandomHanke(0)

        val hanke = hankeService.loadPublicHanke().single()

        // RandomHankeGenerator creates an owner and one other contact
        val omistaja = hanke.omistajat.single()
        assertThat(omistaja.nimi).isNotNull()
        assertThat(omistaja.email).isNotNull()
        assertThat(omistaja.puhelinnumero).isNotNull()
        assertThat(omistaja.tyyppi).isNotNull()
        assertThat(omistaja).isNotNull()
        val yhteystiedot = hanke.rakennuttajat + hanke.toteuttajat + hanke.muut
        val yhteystieto = yhteystiedot.single()
        assertThat(yhteystieto.nimi).isNotNull()
        assertThat(yhteystieto.email).isNotNull()
        assertThat(yhteystieto.puhelinnumero).isNotNull()
        assertThat(yhteystieto.tyyppi).isNotNull()
    }

    @Test
    fun `creates Hanke with hankekayttaja having admin permissions`() {
        randomHankeGenerator.createRandomHanke(0)

        val hanke = hankeService.loadPublicHanke().single()
        val hankekayttajat = hankekayttajaRepository.findByHankeId(hanke.id)

        assertThat(hankekayttajat).hasSize(1)
        val hankekayttaja = hankekayttajat.first()
        assertThat(hankekayttaja.permission!!.kayttooikeustaso)
            .isEqualTo(Kayttooikeustaso.KAIKKI_OIKEUDET)
    }

    @Test
    fun `creates Hanke that can be loaded by public service methods`() {
        randomHankeGenerator.createRandomHanke(0)

        val publicHanke = hankeService.loadPublicHanke()

        assertThat(publicHanke).hasSize(1)
        val hanke = publicHanke.first()
        assertThat(hanke).all {
            prop(Hanke::status).isEqualTo(HankeStatus.PUBLIC)
            prop(Hanke::generated).isFalse()
            prop(Hanke::alueet).hasSize(1)
        }
    }

    @Test
    fun `creates Hanke with tormaystarkastelu calculated`() {
        randomHankeGenerator.createRandomHanke(0)

        val hanke = hankeService.loadPublicHanke().first()
        val hankealue = hanke.alueet.first()

        assertThat(hankealue.tormaystarkasteluTulos).isNotNull()
        assertThat(hankealue.tormaystarkasteluTulos!!.autoliikenne).isNotNull()
    }

    @Test
    fun `creates multiple Hanke with different indices successfully`() {
        repeat(5) { index -> randomHankeGenerator.createRandomHanke(index) }

        val allHanke = hankeService.loadPublicHanke()
        assertThat(allHanke).hasSize(5)
        allHanke.forEach { hanke ->
            assertThat(hanke.status).isEqualTo(HankeStatus.PUBLIC)
            assertThat(hanke.generated).isFalse()
        }
    }

    @Test
    fun `creates Hanke with valid geometries`() {
        randomHankeGenerator.createRandomHanke(0)

        val hanke = hankeService.loadPublicHanke().first()
        val hankealue = hanke.alueet.first()

        assertThat(hankealue.geometriat).isNotNull()
        // The generated geometries should be valid GeoJSON and contain coordinate data
        assertThat(hankealue.geometriat!!.featureCollection).isNotNull()
        assertThat(hankealue.geometriat!!.featureCollection!!.features).hasSize(1)
    }
}

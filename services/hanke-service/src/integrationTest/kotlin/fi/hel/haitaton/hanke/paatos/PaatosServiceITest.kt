package fi.hel.haitaton.hanke.paatos

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.PaatosFactory
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.paatos.PaatosTila.KORVATTU
import fi.hel.haitaton.hanke.paatos.PaatosTila.NYKYINEN
import fi.hel.haitaton.hanke.paatos.PaatosTyyppi.PAATOS
import fi.hel.haitaton.hanke.paatos.PaatosTyyppi.TOIMINNALLINEN_KUNTO
import fi.hel.haitaton.hanke.paatos.PaatosTyyppi.TYO_VALMIS
import fi.hel.haitaton.hanke.test.USERNAME
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PaatosServiceITest(
    @Autowired private val paatosService: PaatosService,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val paatosFactory: PaatosFactory,
) : IntegrationTest() {

    @Nested
    inner class FindByHakemusId {

        @Test
        fun `returns empty list when there are no decisions`() {
            val result = paatosService.findByHakemusId(100L)

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns list of all decisions when they exist`() {
            val hakemus =
                hakemusFactory
                    .builder(USERNAME, applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .save()
            paatosFactory.save(hakemus, "KP2400001", PAATOS, KORVATTU)
            paatosFactory.save(hakemus, "KP2400001-1", PAATOS, KORVATTU)
            paatosFactory.save(hakemus, "KP2400001-2", PAATOS, KORVATTU)
            paatosFactory.save(hakemus, "KP2400001-3", PAATOS, NYKYINEN)
            paatosFactory.save(hakemus, "KP2400001-1", TOIMINNALLINEN_KUNTO, KORVATTU)
            paatosFactory.save(hakemus, "KP2400001-2", TOIMINNALLINEN_KUNTO, KORVATTU)
            paatosFactory.save(hakemus, "KP2400001-3", TOIMINNALLINEN_KUNTO, NYKYINEN)
            paatosFactory.save(hakemus, "KP2400001-3", TYO_VALMIS, NYKYINEN)

            val result = paatosService.findByHakemusId(hakemus.id)

            assertThat(result).hasSize(8)
            assertThat(result)
                .extracting { t -> listOf(t.hakemustunnus, t.tyyppi.toString(), t.tila.toString()) }
                .containsExactly(
                    listOf("KP2400001", PAATOS.toString(), KORVATTU.toString()),
                    listOf("KP2400001-1", PAATOS.toString(), KORVATTU.toString()),
                    listOf("KP2400001-2", PAATOS.toString(), KORVATTU.toString()),
                    listOf("KP2400001-3", PAATOS.toString(), NYKYINEN.toString()),
                    listOf("KP2400001-1", TOIMINNALLINEN_KUNTO.toString(), KORVATTU.toString()),
                    listOf("KP2400001-2", TOIMINNALLINEN_KUNTO.toString(), KORVATTU.toString()),
                    listOf("KP2400001-3", TOIMINNALLINEN_KUNTO.toString(), NYKYINEN.toString()),
                    listOf("KP2400001-3", TYO_VALMIS.toString(), NYKYINEN.toString()),
                )
        }

        @Test
        fun `returns decisions only for the requested application when they exist for several`() {
            val hakemus1 =
                hakemusFactory
                    .builder(USERNAME, applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(alluId = 11)
                    .withName("First")
                    .save()
            val hakemus2 =
                hakemusFactory
                    .builder(USERNAME, applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(alluId = 12)
                    .withName("Second")
                    .save()
            val hakemus3 =
                hakemusFactory
                    .builder(USERNAME, applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(alluId = 13)
                    .withName("Third")
                    .save()
            paatosFactory.save(hakemus1)
            paatosFactory.save(hakemus2)
            paatosFactory.save(hakemus3)

            val result = paatosService.findByHakemusId(hakemus2.id)

            assertThat(result).extracting { it.hakemusId }.containsExactly(hakemus2.id)
        }
    }
}

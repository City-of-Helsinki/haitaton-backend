package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.withHankealue
import fi.hel.haitaton.hanke.factory.HankeFactory.withTormaystarkasteluTulos
import fi.hel.haitaton.hanke.factory.HankeFactory.withYhteystiedot
import fi.hel.haitaton.hanke.touch
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class CompleteHankeValidatorTest {
    private fun completeHanke() =
        HankeFactory.create().withHankealue().withYhteystiedot().withTormaystarkasteluTulos()

    @Test
    fun `true when hanke has all mandatory fields`() {
        assertTrue(CompleteHankeValidator.isHankeComplete(completeHanke()))
    }

    @Test
    fun `true when arvioijat missing`() {
        assertTrue(
            CompleteHankeValidator.isHankeComplete(
                completeHanke().apply { arvioijat = mutableListOf() }
            )
        )
    }

    @Test
    fun `true when toteuttajat missing`() {
        assertTrue(
            CompleteHankeValidator.isHankeComplete(
                completeHanke().apply { toteuttajat = mutableListOf() }
            )
        )
    }

    @ParameterizedTest(name = "false when {0}")
    @MethodSource("draftHankkeet")
    fun `false when hanke is missing a mandatory field`(case: String, hanke: Hanke) {
        case.touch()
        assertFalse(CompleteHankeValidator.isHankeComplete(hanke))
    }

    private fun draftHankkeet(): Stream<Arguments> =
        Stream.of(
            Arguments.of("nimi missing", completeHanke().apply { nimi = null }),
            Arguments.of("nimi empty", completeHanke().apply { nimi = "" }),
            Arguments.of("nimi blank", completeHanke().apply { nimi = "   \t\n\t   " }),
            Arguments.of("kuvaus missing", completeHanke().apply { kuvaus = null }),
            Arguments.of("kuvaus empty", completeHanke().apply { kuvaus = "" }),
            Arguments.of("kuvaus blank", completeHanke().apply { kuvaus = "   \t\n\t   " }),
            Arguments.of(
                "tyomaaKatuosoite missing",
                completeHanke().apply { tyomaaKatuosoite = null }
            ),
            Arguments.of("tyomaaKatuosoite empty", completeHanke().apply { tyomaaKatuosoite = "" }),
            Arguments.of(
                "tyomaaKatuosoite blank",
                completeHanke().apply { tyomaaKatuosoite = "   \t\n\t   " }
            ),
            Arguments.of("vaihe missing", completeHanke().apply { vaihe = null }),
            Arguments.of(
                "suunnitteluVaihe missing",
                completeHanke().apply {
                    vaihe = Vaihe.SUUNNITTELU
                    suunnitteluVaihe = null
                }
            ),
            Arguments.of("alueet empty", completeHanke().apply { alueet = mutableListOf() }),
            Arguments.of(
                "alueet.haittaAlkuPvm missing",
                completeHanke().apply { alueet[0].haittaAlkuPvm = null }
            ),
            Arguments.of(
                "alueet.haittaLoppuPvm missing",
                completeHanke().apply { alueet[0].haittaLoppuPvm = null }
            ),
            Arguments.of(
                "alueet.meluHaitta missing",
                completeHanke().apply { alueet[0].meluHaitta = null }
            ),
            Arguments.of(
                "alueet.polyHaitta missing",
                completeHanke().apply { alueet[0].polyHaitta = null }
            ),
            Arguments.of(
                "alueet.tarinaHaitta missing",
                completeHanke().apply { alueet[0].tarinaHaitta = null }
            ),
            Arguments.of(
                "alueet.kaistaHaitta missing",
                completeHanke().apply { alueet[0].kaistaHaitta = null }
            ),
            Arguments.of(
                "alueet.kaistaPituusHaitta missing",
                completeHanke().apply { alueet[0].kaistaPituusHaitta = null }
            ),
            Arguments.of(
                "alueet.geometriat missing",
                completeHanke().apply { alueet[0].geometriat = null }
            ),
            Arguments.of(
                "alueet.geometriat.featureCollection missing",
                completeHanke().apply { alueet[0].geometriat!!.featureCollection = null }
            ),
            Arguments.of(
                "alueet.geometriat.featureCollection.features missing",
                completeHanke().apply { alueet[0].geometriat!!.featureCollection!!.features = null }
            ),
            Arguments.of(
                "alueet.geometriat.featureCollection.features empty",
                completeHanke().apply {
                    alueet[0].geometriat!!.featureCollection!!.features = listOf()
                }
            ),
            Arguments.of("omistajat empty", completeHanke().apply { omistajat = mutableListOf() }),
            Arguments.of(
                "omistajat.etunimi empty",
                completeHanke().apply { omistajat[0].etunimi = "" }
            ),
            Arguments.of(
                "omistajat.etunimi blank",
                completeHanke().apply { omistajat[0].etunimi = "   \t\n\t   " }
            ),
            Arguments.of(
                "omistajat.sukunimi empty",
                completeHanke().apply { omistajat[0].sukunimi = "" }
            ),
            Arguments.of(
                "omistajat.sukunimi blank",
                completeHanke().apply { omistajat[0].sukunimi = "   \t\n\t   " }
            ),
            Arguments.of(
                "omistajat.email empty",
                completeHanke().apply { omistajat[0].email = "" }
            ),
            Arguments.of(
                "omistajat.email blank",
                completeHanke().apply { omistajat[0].email = "   \t\n\t   " }
            ),
            Arguments.of(
                "arvioijat.etunimi empty",
                completeHanke().apply { arvioijat[0].etunimi = "" }
            ),
            Arguments.of(
                "arvioijat.etunimi blank",
                completeHanke().apply { arvioijat[0].etunimi = "   \t\n\t   " }
            ),
            Arguments.of(
                "arvioijat.sukunimi empty",
                completeHanke().apply { arvioijat[0].sukunimi = "" }
            ),
            Arguments.of(
                "arvioijat.sukunimi blank",
                completeHanke().apply { arvioijat[0].sukunimi = "   \t\n\t   " }
            ),
            Arguments.of(
                "arvioijat.email empty",
                completeHanke().apply { arvioijat[0].email = "" }
            ),
            Arguments.of(
                "arvioijat.email blank",
                completeHanke().apply { arvioijat[0].email = "   \t\n\t   " }
            ),
            Arguments.of(
                "toteuttajat.etunimi empty",
                completeHanke().apply { toteuttajat[0].etunimi = "" }
            ),
            Arguments.of(
                "toteuttajat.etunimi blank",
                completeHanke().apply { toteuttajat[0].etunimi = "   \t\n\t   " }
            ),
            Arguments.of(
                "toteuttajat.sukunimi empty",
                completeHanke().apply { toteuttajat[0].sukunimi = "" }
            ),
            Arguments.of(
                "toteuttajat.sukunimi blank",
                completeHanke().apply { toteuttajat[0].sukunimi = "   \t\n\t   " }
            ),
            Arguments.of(
                "toteuttajat.email empty",
                completeHanke().apply { toteuttajat[0].email = "" }
            ),
            Arguments.of(
                "toteuttajat.email blank",
                completeHanke().apply { toteuttajat[0].email = "   \t\n\t   " }
            ),
            Arguments.of(
                "tormaystarkasteluTulos missing",
                completeHanke().apply { tormaystarkasteluTulos = null }
            ),
        )
}

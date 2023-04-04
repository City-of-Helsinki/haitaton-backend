package fi.hel.haitaton.hanke.validation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withHankealue
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withTormaystarkasteluTulos
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
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
internal class HankePublicValidatorTest {
    private fun completeHanke() =
        HankeFactory.create().withHankealue().withYhteystiedot().withTormaystarkasteluTulos()

    @Test
    fun `No errors when hanke has all mandatory fields`() {
        val result = HankePublicValidator.validateHankeHasMandatoryFields(completeHanke())

        assertTrue(result.isOk())
    }

    @Test
    fun `No errors when arvioijat missing`() {
        val result =
            HankePublicValidator.validateHankeHasMandatoryFields(
                completeHanke().apply { arvioijat = mutableListOf() }
            )

        assertTrue(result.isOk())
    }

    @Test
    fun `No errors when toteuttajat missing`() {
        val result =
            HankePublicValidator.validateHankeHasMandatoryFields(
                completeHanke().apply { toteuttajat = mutableListOf() }
            )

        assertTrue(result.isOk())
    }

    @Test
    fun `All errors when multiple missing fields`() {
        val result =
            HankePublicValidator.validateHankeHasMandatoryFields(
                completeHanke().apply {
                    toteuttajat[0].etunimi = ""
                    alueet[0].geometriat!!.featureCollection!!.features = null
                    kuvaus = null
                }
            )

        assertFalse(result.isOk())
        assertThat(result.errorPaths())
            .containsExactlyInAnyOrder(
                "toteuttajat[0].etunimi",
                "alueet[0].geometriat.featureCollection.features",
                "kuvaus",
            )
    }

    @ParameterizedTest(name = "Has error when {0} {1}")
    @MethodSource("draftHankkeet")
    fun `Error with correct path when hanke is missing a mandatory field`(
        path: String,
        case: String,
        hanke: Hanke
    ) {
        case.touch()

        val result = HankePublicValidator.validateHankeHasMandatoryFields(hanke)

        assertFalse(result.isOk())
        assertThat(result.errorPaths()).containsExactly(path)
    }

    private fun draftHankkeet(): Stream<Arguments> =
        Stream.of(
            Arguments.of("nimi", "missing", completeHanke().apply { nimi = null }),
            Arguments.of("nimi", "empty", completeHanke().apply { nimi = "" }),
            Arguments.of("nimi", "blank", completeHanke().apply { nimi = "   \t\n\t   " }),
            Arguments.of("kuvaus", "missing", completeHanke().apply { kuvaus = null }),
            Arguments.of("kuvaus", "empty", completeHanke().apply { kuvaus = "" }),
            Arguments.of("kuvaus", "blank", completeHanke().apply { kuvaus = "   \t\n\t   " }),
            Arguments.of(
                "tyomaaKatuosoite",
                "missing",
                completeHanke().apply { tyomaaKatuosoite = null }
            ),
            Arguments.of(
                "tyomaaKatuosoite",
                "empty",
                completeHanke().apply { tyomaaKatuosoite = "" }
            ),
            Arguments.of(
                "tyomaaKatuosoite",
                "blank",
                completeHanke().apply { tyomaaKatuosoite = "   \t\n\t   " }
            ),
            Arguments.of("vaihe", "missing", completeHanke().apply { vaihe = null }),
            Arguments.of(
                "suunnitteluVaihe",
                "missing",
                completeHanke().apply {
                    vaihe = Vaihe.SUUNNITTELU
                    suunnitteluVaihe = null
                }
            ),
            Arguments.of("alueet", "empty", completeHanke().apply { alueet = mutableListOf() }),
            Arguments.of(
                "alueet[0].haittaAlkuPvm",
                "missing",
                completeHanke().apply { alueet[0].haittaAlkuPvm = null }
            ),
            Arguments.of(
                "alueet[0].haittaLoppuPvm",
                "missing",
                completeHanke().apply { alueet[0].haittaLoppuPvm = null }
            ),
            Arguments.of(
                "alueet[0].meluHaitta",
                "missing",
                completeHanke().apply { alueet[0].meluHaitta = null }
            ),
            Arguments.of(
                "alueet[0].polyHaitta",
                "missing",
                completeHanke().apply { alueet[0].polyHaitta = null }
            ),
            Arguments.of(
                "alueet[0].tarinaHaitta",
                "missing",
                completeHanke().apply { alueet[0].tarinaHaitta = null }
            ),
            Arguments.of(
                "alueet[0].kaistaHaitta",
                "missing",
                completeHanke().apply { alueet[0].kaistaHaitta = null }
            ),
            Arguments.of(
                "alueet[0].kaistaPituusHaitta",
                "missing",
                completeHanke().apply { alueet[0].kaistaPituusHaitta = null }
            ),
            Arguments.of(
                "alueet[0].geometriat",
                "missing",
                completeHanke().apply { alueet[0].geometriat = null }
            ),
            Arguments.of(
                "alueet[0].geometriat.featureCollection",
                "missing",
                completeHanke().apply { alueet[0].geometriat!!.featureCollection = null }
            ),
            Arguments.of(
                "alueet[0].geometriat.featureCollection.features",
                "missing",
                completeHanke().apply { alueet[0].geometriat!!.featureCollection!!.features = null }
            ),
            Arguments.of(
                "alueet[0].geometriat.featureCollection.features",
                "empty",
                completeHanke().apply {
                    alueet[0].geometriat!!.featureCollection!!.features = listOf()
                }
            ),
            Arguments.of(
                "omistajat",
                "empty",
                completeHanke().apply { omistajat = mutableListOf() }
            ),
            Arguments.of(
                "omistajat[0].etunimi",
                "empty",
                completeHanke().apply { omistajat[0].etunimi = "" }
            ),
            Arguments.of(
                "omistajat[0].etunimi",
                "blank",
                completeHanke().apply { omistajat[0].etunimi = "   \t\n\t   " }
            ),
            Arguments.of(
                "omistajat[0].sukunimi",
                "empty",
                completeHanke().apply { omistajat[0].sukunimi = "" }
            ),
            Arguments.of(
                "omistajat[0].sukunimi",
                "blank",
                completeHanke().apply { omistajat[0].sukunimi = "   \t\n\t   " }
            ),
            Arguments.of(
                "omistajat[0].email",
                "empty",
                completeHanke().apply { omistajat[0].email = "" }
            ),
            Arguments.of(
                "omistajat[0].email",
                "blank",
                completeHanke().apply { omistajat[0].email = "   \t\n\t   " }
            ),
            Arguments.of(
                "arvioijat[0].etunimi",
                "empty",
                completeHanke().apply { arvioijat[0].etunimi = "" }
            ),
            Arguments.of(
                "arvioijat[0].etunimi",
                "blank",
                completeHanke().apply { arvioijat[0].etunimi = "   \t\n\t   " }
            ),
            Arguments.of(
                "arvioijat[0].sukunimi",
                "empty",
                completeHanke().apply { arvioijat[0].sukunimi = "" }
            ),
            Arguments.of(
                "arvioijat[0].sukunimi",
                "blank",
                completeHanke().apply { arvioijat[0].sukunimi = "   \t\n\t   " }
            ),
            Arguments.of(
                "arvioijat[0].email",
                "empty",
                completeHanke().apply { arvioijat[0].email = "" }
            ),
            Arguments.of(
                "arvioijat[0].email",
                "blank",
                completeHanke().apply { arvioijat[0].email = "   \t\n\t   " }
            ),
            Arguments.of(
                "toteuttajat[0].etunimi",
                "empty",
                completeHanke().apply { toteuttajat[0].etunimi = "" }
            ),
            Arguments.of(
                "toteuttajat[0].etunimi",
                "blank",
                completeHanke().apply { toteuttajat[0].etunimi = "   \t\n\t   " }
            ),
            Arguments.of(
                "toteuttajat[0].sukunimi",
                "empty",
                completeHanke().apply { toteuttajat[0].sukunimi = "" }
            ),
            Arguments.of(
                "toteuttajat[0].sukunimi",
                "blank",
                completeHanke().apply { toteuttajat[0].sukunimi = "   \t\n\t   " }
            ),
            Arguments.of(
                "toteuttajat[0].email",
                "empty",
                completeHanke().apply { toteuttajat[0].email = "" }
            ),
            Arguments.of(
                "toteuttajat[0].email",
                "blank",
                completeHanke().apply { toteuttajat[0].email = "   \t\n\t   " }
            ),
        )
}

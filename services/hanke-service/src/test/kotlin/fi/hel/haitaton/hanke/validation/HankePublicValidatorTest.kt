package fi.hel.haitaton.hanke.validation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import fi.hel.haitaton.hanke.Alikontakti
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

private const val BLANK = "   \t\n\t   "

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
    fun `Empty alikontaktit is ok`() {
        val hanke = completeHanke().apply { omistajat.first().apply { alikontaktit = emptyList() } }

        val result = HankePublicValidator.validateHankeHasMandatoryFields(hanke)

        assertTrue(result.isOk())
    }

    @Test
    fun `Alikontaktit missing data is not ok`() {
        val hanke =
            completeHanke().apply {
                omistajat.first().apply { alikontaktit = listOf(Alikontakti("", "", "", "")) }
            }

        val result = HankePublicValidator.validateHankeHasMandatoryFields(hanke)

        assertFalse(result.isOk())
        assertThat(result.errorPaths())
            .containsExactlyInAnyOrder(
                "alikontaktit[0].etunimi",
                "alikontaktit[0].sukunimi",
                "alikontaktit[0].email",
                "alikontaktit[0].puhelinnumero"
            )
    }

    @Test
    fun `No errors when rakennuttajat missing`() {
        val result =
            HankePublicValidator.validateHankeHasMandatoryFields(
                completeHanke().apply { rakennuttajat = mutableListOf() }
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
                    with(toteuttajat[0]) {
                        nimi = ""
                        email = ""
                    }

                    alueet[0].geometriat!!.featureCollection!!.features = null
                    kuvaus = null
                }
            )

        assertFalse(result.isOk())
        assertThat(result.errorPaths())
            .containsExactlyInAnyOrder(
                "toteuttajat[0].nimi",
                "toteuttajat[0].email",
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
            Arguments.of("nimi", "blank", completeHanke().apply { nimi = BLANK }),
            Arguments.of("kuvaus", "missing", completeHanke().apply { kuvaus = null }),
            Arguments.of("kuvaus", "empty", completeHanke().apply { kuvaus = "" }),
            Arguments.of("kuvaus", "blank", completeHanke().apply { kuvaus = BLANK }),
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
                completeHanke().apply { tyomaaKatuosoite = BLANK }
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
                "omistajat[0].nimi",
                "empty",
                completeHanke().apply { omistajat[0].nimi = "" }
            ),
            Arguments.of(
                "omistajat[0].nimi",
                "blank",
                completeHanke().apply { omistajat[0].nimi = BLANK }
            ),
            Arguments.of(
                "omistajat[0].email",
                "empty",
                completeHanke().apply { omistajat[0].email = "" }
            ),
            Arguments.of(
                "omistajat[0].email",
                "blank",
                completeHanke().apply { omistajat[0].email = BLANK }
            ),
            Arguments.of(
                "rakennuttajat[0].nimi",
                "empty",
                completeHanke().apply { rakennuttajat[0].nimi = "" }
            ),
            Arguments.of(
                "rakennuttajat[0].nimi",
                "blank",
                completeHanke().apply { rakennuttajat[0].nimi = BLANK }
            ),
            Arguments.of(
                "rakennuttajat[0].email",
                "empty",
                completeHanke().apply { rakennuttajat[0].email = "" }
            ),
            Arguments.of(
                "rakennuttajat[0].email",
                "blank",
                completeHanke().apply { rakennuttajat[0].email = BLANK }
            ),
            Arguments.of(
                "toteuttajat[0].nimi",
                "empty",
                completeHanke().apply { toteuttajat[0].nimi = "" }
            ),
            Arguments.of(
                "toteuttajat[0].nimi",
                "blank",
                completeHanke().apply { toteuttajat[0].nimi = BLANK }
            ),
            Arguments.of(
                "toteuttajat[0].email",
                "empty",
                completeHanke().apply { toteuttajat[0].email = "" }
            ),
            Arguments.of(
                "toteuttajat[0].email",
                "blank",
                completeHanke().apply { toteuttajat[0].email = BLANK }
            ),
        )
}

package fi.hel.haitaton.hanke.validation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import fi.hel.haitaton.hanke.Yhteyshenkilo
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YKSITYISHENKILO
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withHankealue
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withTormaystarkasteluTulos
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.factory.modify
import fi.hel.haitaton.hanke.touch
import fi.hel.haitaton.hanke.validation.HankePublicValidator.validateHankeHasMandatoryFields
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

private const val BLANK = "   \t\n\t   "

class HankePublicValidatorTest {

    companion object {
        private fun completeHanke() =
            HankeFactory.create().withHankealue().withYhteystiedot().withTormaystarkasteluTulos()

        @JvmStatic
        private fun draftHankkeet(): Stream<Arguments> =
            Stream.of(
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
                    completeHanke().apply {
                        alueet[0].geometriat!!.featureCollection!!.features = null
                    }
                ),
                Arguments.of(
                    "alueet[0].geometriat.featureCollection.features",
                    "empty",
                    completeHanke().apply {
                        alueet[0].geometriat!!.featureCollection!!.features = listOf()
                    }
                ),
                Arguments.of(
                    "alueet[0].nimi",
                    "empty",
                    completeHanke().apply { alueet[0].nimi = "" }
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

    @Test
    fun `when hanke has all mandatory fields should return ok`() {
        val result = validateHankeHasMandatoryFields(completeHanke())

        assertTrue(result.isOk())
    }

    @Test
    fun `when alikontaktit empty should return ok`() {
        val hanke = completeHanke().apply { omistajat.first().apply { alikontaktit = emptyList() } }

        val result = validateHankeHasMandatoryFields(hanke)

        assertTrue(result.isOk())
    }

    @Test
    fun `when alikontaktit missing data should return not ok`() {
        val hanke =
            completeHanke().apply {
                omistajat.first().apply { alikontaktit = listOf(Yhteyshenkilo("", "", "", "")) }
            }

        val result = validateHankeHasMandatoryFields(hanke)

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
    fun `when rakennuttajat missing should return ok`() {
        val result =
            validateHankeHasMandatoryFields(
                completeHanke().apply { rakennuttajat = mutableListOf() }
            )

        assertTrue(result.isOk())
    }

    @Test
    fun `when toteuttajat missing should return ok`() {
        val result =
            validateHankeHasMandatoryFields(completeHanke().apply { toteuttajat = mutableListOf() })

        assertTrue(result.isOk())
    }

    @Test
    fun `when ytunnus is present and valid should return ok`() {
        val hanke = completeHanke()

        val result = validateHankeHasMandatoryFields(hanke)

        val ytunnusCount = hanke.extractYhteystiedot().mapNotNull { it.ytunnus }.count()
        assertThat(ytunnusCount).isGreaterThanOrEqualTo(1)
        assertTrue(result.isOk())
    }

    @Test
    fun `when ytunnus is present and not valid should return not ok`() {
        val hanke =
            completeHanke().apply { rakennuttajat = rakennuttajat.modify(ytunnus = "1580375-3") }

        val result = validateHankeHasMandatoryFields(hanke)

        assertFalse(result.isOk())
        assertThat(result.errorPaths()).containsExactly("rakennuttajat[0].ytunnus")
    }

    @Test
    fun `when tyyppi yksityishenkilo or null and ytunnus is null should return ok`() {
        val hanke =
            completeHanke().apply {
                omistajat = omistajat.modify(ytunnus = null, tyyppi = null)
                rakennuttajat = rakennuttajat.modify(ytunnus = null, tyyppi = YKSITYISHENKILO)
                toteuttajat = toteuttajat.modify(ytunnus = null, null)
                muut = muut.modify(ytunnus = null, YKSITYISHENKILO)
            }

        val result = validateHankeHasMandatoryFields(hanke)

        val ytunnusCount = hanke.extractYhteystiedot().mapNotNull { it.ytunnus }.count()
        assertThat(ytunnusCount).isEqualTo(0)
        assertTrue(result.isOk())
    }

    @Test
    fun `when tyyppi is not yksityishenkilo and ytunnus is null should not return ok`() {
        val hanke = completeHanke().apply { toteuttajat = toteuttajat.modify(ytunnus = null) }

        val result = validateHankeHasMandatoryFields(hanke)

        assertFalse(result.isOk())
        assertThat(result.errorPaths()).containsExactly("toteuttajat[0].ytunnus")
    }

    @Test
    fun `when tyyppi is yksityishenkilo and ytunnus is not null should not return ok`() {
        val hanke = completeHanke().apply { omistajat = omistajat.modify(tyyppi = YKSITYISHENKILO) }

        val result = validateHankeHasMandatoryFields(hanke)

        assertFalse(result.isOk())
        assertThat(result.errorPaths()).containsExactly("omistajat[0].ytunnus")
    }

    @Test
    fun `when multiple missing fields should return not ok and failed paths`() {
        val result =
            validateHankeHasMandatoryFields(
                completeHanke().apply {
                    with(toteuttajat[0]) {
                        nimi = ""
                        email = ""
                    }

                    alueet[0].geometriat!!.featureCollection!!.features = null
                    alueet[0].nimi = ""
                    kuvaus = null
                }
            )

        assertFalse(result.isOk())
        assertThat(result.errorPaths())
            .containsExactlyInAnyOrder(
                "toteuttajat[0].nimi",
                "toteuttajat[0].email",
                "alueet[0].geometriat.featureCollection.features",
                "alueet[0].nimi",
                "kuvaus",
            )
    }

    @ParameterizedTest(name = "Has error when {0} {1}")
    @MethodSource("draftHankkeet")
    fun `when hanke missing a mandatory field should return not ok and failed path`(
        path: String,
        case: String,
        hanke: Hanke
    ) {
        case.touch()

        val result = validateHankeHasMandatoryFields(hanke)

        assertFalse(result.isOk())
        assertThat(result.errorPaths()).containsExactly(path)
    }
}

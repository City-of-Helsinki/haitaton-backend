package fi.hel.haitaton.hanke.validation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import fi.hel.haitaton.hanke.domain.Haittojenhallintatyyppi
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YKSITYISHENKILO
import fi.hel.haitaton.hanke.factory.HaittaFactory
import fi.hel.haitaton.hanke.factory.HaittaFactory.TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU
import fi.hel.haitaton.hanke.factory.HaittaFactory.TORMAYSTARKASTELU_ZERO_AUTOLIIKENNELUOKITTELU
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withHankealue
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.factory.modify
import fi.hel.haitaton.hanke.test.Asserts.failedWith
import fi.hel.haitaton.hanke.test.Asserts.isSuccess
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.touch
import fi.hel.haitaton.hanke.validation.HankePublicValidator.validateHankeHasMandatoryFields
import java.util.stream.Stream
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

private const val BLANK = "   \t\n\t   "

class HankePublicValidatorTest {

    companion object {
        private fun completeHanke() = HankeFactory.create().withHankealue().withYhteystiedot()

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
                    completeHanke().apply { tyomaaKatuosoite = null },
                ),
                Arguments.of(
                    "tyomaaKatuosoite",
                    "empty",
                    completeHanke().apply { tyomaaKatuosoite = "" },
                ),
                Arguments.of(
                    "tyomaaKatuosoite",
                    "blank",
                    completeHanke().apply { tyomaaKatuosoite = BLANK },
                ),
                Arguments.of("vaihe", "missing", completeHanke().apply { vaihe = null }),
                Arguments.of("alueet", "empty", completeHanke().apply { alueet = mutableListOf() }),
                Arguments.of(
                    "alueet[0].haittaAlkuPvm",
                    "missing",
                    completeHanke().apply { alueet[0].haittaAlkuPvm = null },
                ),
                Arguments.of(
                    "alueet[0].haittaLoppuPvm",
                    "missing",
                    completeHanke().apply { alueet[0].haittaLoppuPvm = null },
                ),
                Arguments.of(
                    "alueet[0].meluHaitta",
                    "missing",
                    completeHanke().apply { alueet[0].meluHaitta = null },
                ),
                Arguments.of(
                    "alueet[0].polyHaitta",
                    "missing",
                    completeHanke().apply { alueet[0].polyHaitta = null },
                ),
                Arguments.of(
                    "alueet[0].tarinaHaitta",
                    "missing",
                    completeHanke().apply { alueet[0].tarinaHaitta = null },
                ),
                Arguments.of(
                    "alueet[0].kaistaHaitta",
                    "missing",
                    completeHanke().apply { alueet[0].kaistaHaitta = null },
                ),
                Arguments.of(
                    "alueet[0].kaistaPituusHaitta",
                    "missing",
                    completeHanke().apply { alueet[0].kaistaPituusHaitta = null },
                ),
                Arguments.of(
                    "alueet[0].geometriat",
                    "missing",
                    completeHanke().apply { alueet[0].geometriat = null },
                ),
                Arguments.of(
                    "alueet[0].geometriat.featureCollection",
                    "missing",
                    completeHanke().apply { alueet[0].geometriat!!.featureCollection = null },
                ),
                Arguments.of(
                    "alueet[0].geometriat.featureCollection.features",
                    "missing",
                    completeHanke().apply {
                        alueet[0].geometriat!!.featureCollection!!.features = null
                    },
                ),
                Arguments.of(
                    "alueet[0].geometriat.featureCollection.features",
                    "empty",
                    completeHanke().apply {
                        alueet[0].geometriat!!.featureCollection!!.features = listOf()
                    },
                ),
                Arguments.of(
                    "alueet[0].nimi",
                    "empty",
                    completeHanke().apply { alueet[0].nimi = "" },
                ),
                Arguments.of(
                    "alueet[0].haittojenhallintasuunnitelma",
                    "missing",
                    completeHanke().apply { alueet[0].haittojenhallintasuunnitelma = null },
                ),
                Arguments.of(
                    "alueet[0].haittojenhallintasuunnitelma.YLEINEN",
                    "missing",
                    completeHanke().apply {
                        alueet[0] =
                            alueet[0].copy(
                                haittojenhallintasuunnitelma =
                                    HaittaFactory.createHaittojenhallintasuunnitelma(
                                        Haittojenhallintatyyppi.YLEINEN to null
                                    ),
                                tormaystarkasteluTulos = null,
                            )
                    },
                ),
                Arguments.of(
                    "alueet[0].haittojenhallintasuunnitelma.AUTOLIIKENNE",
                    "empty",
                    completeHanke().apply {
                        alueet[0].haittojenhallintasuunnitelma =
                            HaittaFactory.createHaittojenhallintasuunnitelma(
                                Haittojenhallintatyyppi.AUTOLIIKENNE to ""
                            )
                    },
                ),
                Arguments.of(
                    "alueet[0].haittojenhallintasuunnitelma.MUUT",
                    "blank",
                    completeHanke().apply {
                        alueet[0].haittojenhallintasuunnitelma =
                            HaittaFactory.createHaittojenhallintasuunnitelma(
                                Haittojenhallintatyyppi.MUUT to BLANK
                            )
                    },
                ),
                Arguments.of(
                    "omistajat",
                    "empty",
                    completeHanke().apply { omistajat = mutableListOf() },
                ),
                Arguments.of(
                    "omistajat[0].nimi",
                    "empty",
                    completeHanke().apply { omistajat[0].nimi = "" },
                ),
                Arguments.of(
                    "omistajat[0].nimi",
                    "blank",
                    completeHanke().apply { omistajat[0].nimi = BLANK },
                ),
                Arguments.of(
                    "omistajat[0].email",
                    "empty",
                    completeHanke().apply { omistajat[0].email = "" },
                ),
                Arguments.of(
                    "omistajat[0].email",
                    "blank",
                    completeHanke().apply { omistajat[0].email = BLANK },
                ),
                Arguments.of(
                    "rakennuttajat[0].nimi",
                    "empty",
                    completeHanke().apply { rakennuttajat[0].nimi = "" },
                ),
                Arguments.of(
                    "rakennuttajat[0].nimi",
                    "blank",
                    completeHanke().apply { rakennuttajat[0].nimi = BLANK },
                ),
                Arguments.of(
                    "rakennuttajat[0].email",
                    "empty",
                    completeHanke().apply { rakennuttajat[0].email = "" },
                ),
                Arguments.of(
                    "rakennuttajat[0].email",
                    "blank",
                    completeHanke().apply { rakennuttajat[0].email = BLANK },
                ),
                Arguments.of(
                    "toteuttajat[0].nimi",
                    "empty",
                    completeHanke().apply { toteuttajat[0].nimi = "" },
                ),
                Arguments.of(
                    "toteuttajat[0].nimi",
                    "blank",
                    completeHanke().apply { toteuttajat[0].nimi = BLANK },
                ),
                Arguments.of(
                    "toteuttajat[0].email",
                    "empty",
                    completeHanke().apply { toteuttajat[0].email = "" },
                ),
                Arguments.of(
                    "toteuttajat[0].email",
                    "blank",
                    completeHanke().apply { toteuttajat[0].email = BLANK },
                ),
            )
    }

    @Test
    fun `when hanke has all mandatory fields should return ok`() {
        val result = validateHankeHasMandatoryFields(completeHanke())

        assertThat(result).isSuccess()
    }

    @Test
    fun `when rakennuttajat missing should return ok`() {
        val result =
            validateHankeHasMandatoryFields(
                completeHanke().apply { rakennuttajat = mutableListOf() }
            )

        assertThat(result).isSuccess()
    }

    @Test
    fun `when toteuttajat missing should return ok`() {
        val result =
            validateHankeHasMandatoryFields(completeHanke().apply { toteuttajat = mutableListOf() })

        assertThat(result).isSuccess()
    }

    @Test
    fun `when ytunnus is present and valid should return ok`() {
        val hanke = completeHanke()

        val result = validateHankeHasMandatoryFields(hanke)

        val ytunnusCount = hanke.extractYhteystiedot().mapNotNull { it.ytunnus }.count()
        assertThat(ytunnusCount).isGreaterThanOrEqualTo(1)
        assertThat(result).isSuccess()
    }

    @Test
    fun `when ytunnus is present and not valid should return not ok`() {
        val hanke =
            completeHanke().apply { rakennuttajat = rakennuttajat.modify(ytunnus = "1580375-3") }

        val result = validateHankeHasMandatoryFields(hanke)

        assertThat(result).failedWith("rakennuttajat[0].ytunnus")
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
        assertThat(result).isSuccess()
    }

    @Test
    fun `when tyyppi is not yksityishenkilo and ytunnus is null should not return ok`() {
        val hanke = completeHanke().apply { toteuttajat = toteuttajat.modify(ytunnus = null) }

        val result = validateHankeHasMandatoryFields(hanke)

        assertThat(result).failedWith("toteuttajat[0].ytunnus")
    }

    @Test
    fun `when tyyppi is yksityishenkilo and ytunnus is not null should not return ok`() {
        val hanke = completeHanke().apply { omistajat = omistajat.modify(tyyppi = YKSITYISHENKILO) }

        val result = validateHankeHasMandatoryFields(hanke)

        assertThat(result).failedWith("omistajat[0].ytunnus")
    }

    @Test
    fun `succeeds when haittojenhallintasuunnitelma is missing a value, but tormaystarkastelu is zero for it`() {
        val hanke = completeHanke()
        hanke.alueet[0] =
            hanke.alueet[0].copy(
                haittojenhallintasuunnitelma =
                    HaittaFactory.createHaittojenhallintasuunnitelma(
                        Haittojenhallintatyyppi.PYORALIIKENNE to null
                    ),
                tormaystarkasteluTulos =
                    hanke.alueet[0].tormaystarkasteluTulos!!.copy(pyoraliikenneindeksi = 0f),
            )

        val result = validateHankeHasMandatoryFields(hanke)

        assertThat(result).isSuccess()
    }

    @Test
    fun `succeeds when haittojenhallintasuunnitelma is missing a value, but tormaystarkastelu is null`() {
        val hanke = completeHanke()
        hanke.alueet[0] =
            hanke.alueet[0].copy(
                haittojenhallintasuunnitelma =
                    HaittaFactory.createHaittojenhallintasuunnitelma(
                        Haittojenhallintatyyppi.AUTOLIIKENNE to ""
                    ),
                tormaystarkasteluTulos = null,
            )

        val result = validateHankeHasMandatoryFields(hanke)

        assertThat(result).isSuccess()
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

        assertThat(result)
            .failedWith(
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
        hanke: Hanke,
    ) {
        case.touch()

        val result = validateHankeHasMandatoryFields(hanke)

        assertThat(result).failedWith(path)
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ValidateHaittojenhallintasuunnitelmaLiikennemuodot {

        private val tormaystarkasteluTulos =
            TormaystarkasteluTulos(
                autoliikenne = TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU,
                pyoraliikenneindeksi = 3f,
                linjaautoliikenneindeksi = 3f,
                raitioliikenneindeksi = 3f,
            )

        @Test
        fun `returns success when suunnitelma present and matching tormaystarkastelutulos is positive`() {
            val haittojenhallintasuunnitelma = HaittaFactory.createHaittojenhallintasuunnitelma()

            val result =
                HankePublicValidator.validateHaittojenhallintasuunnitelmaLiikennemuodot(
                    haittojenhallintasuunnitelma,
                    tormaystarkasteluTulos,
                    "hhs",
                )

            assertThat(result).isSuccess()
        }

        @ParameterizedTest
        @EnumSource(
            Haittojenhallintatyyppi::class,
            names = ["YLEINEN", "MUUT"],
            mode = EnumSource.Mode.EXCLUDE,
        )
        fun `fails when suunnitelma is missing and matching tormaystarkastelutulos is positive`(
            tyyppi: Haittojenhallintatyyppi
        ) {
            val haittojenhallintasuunnitelma =
                HaittaFactory.createHaittojenhallintasuunnitelma(tyyppi to null)

            val result =
                HankePublicValidator.validateHaittojenhallintasuunnitelmaLiikennemuodot(
                    haittojenhallintasuunnitelma,
                    tormaystarkasteluTulos,
                    "hhs",
                )

            assertThat(result).failedWith("hhs.$tyyppi")
        }

        @ParameterizedTest
        @EnumSource(
            Haittojenhallintatyyppi::class,
            names = ["YLEINEN", "MUUT"],
            mode = EnumSource.Mode.EXCLUDE,
        )
        fun `fails when suunnitelma is empty and matching tormaystarkastelutulos is positive`(
            tyyppi: Haittojenhallintatyyppi
        ) {
            val haittojenhallintasuunnitelma =
                HaittaFactory.createHaittojenhallintasuunnitelma(tyyppi to "")

            val result =
                HankePublicValidator.validateHaittojenhallintasuunnitelmaLiikennemuodot(
                    haittojenhallintasuunnitelma,
                    tormaystarkasteluTulos,
                    "hhs",
                )

            assertThat(result).failedWith("hhs.$tyyppi")
        }

        @ParameterizedTest
        @EnumSource(
            Haittojenhallintatyyppi::class,
            names = ["YLEINEN", "MUUT"],
            mode = EnumSource.Mode.EXCLUDE,
        )
        fun `fails when suunnitelma is blank and matching tormaystarkastelutulos is positive`(
            tyyppi: Haittojenhallintatyyppi
        ) {
            val haittojenhallintasuunnitelma =
                HaittaFactory.createHaittojenhallintasuunnitelma(tyyppi to BLANK)

            val result =
                HankePublicValidator.validateHaittojenhallintasuunnitelmaLiikennemuodot(
                    haittojenhallintasuunnitelma,
                    tormaystarkasteluTulos,
                    "hhs",
                )

            assertThat(result).failedWith("hhs.$tyyppi")
        }

        @ParameterizedTest
        @MethodSource("zeroes")
        fun `returns success when suunnitelma is missing and matching tormaystarkastelutulos is zero`(
            tyyppi: Haittojenhallintatyyppi,
            tormaystarkasteluTulos: TormaystarkasteluTulos,
        ) {
            val haittojenhallintasuunnitelma = HaittaFactory.createHaittojenhallintasuunnitelma()

            val result =
                HankePublicValidator.validateHaittojenhallintasuunnitelmaLiikennemuodot(
                    haittojenhallintasuunnitelma,
                    tormaystarkasteluTulos,
                    "hhs",
                )

            assertThat(result).isSuccess()
        }

        private fun zeroes(): List<Arguments> =
            listOf(
                Arguments.of(
                    Haittojenhallintatyyppi.AUTOLIIKENNE,
                    tormaystarkasteluTulos.copy(
                        autoliikenne = TORMAYSTARKASTELU_ZERO_AUTOLIIKENNELUOKITTELU
                    ),
                ),
                Arguments.of(
                    Haittojenhallintatyyppi.LINJAAUTOLIIKENNE,
                    tormaystarkasteluTulos.copy(linjaautoliikenneindeksi = 0f),
                ),
                Arguments.of(
                    Haittojenhallintatyyppi.RAITIOLIIKENNE,
                    tormaystarkasteluTulos.copy(raitioliikenneindeksi = 0f),
                ),
                Arguments.of(
                    Haittojenhallintatyyppi.PYORALIIKENNE,
                    tormaystarkasteluTulos.copy(pyoraliikenneindeksi = 0f),
                ),
            )
    }

    @Nested
    inner class ValidateHaittojenhallintasuunnitelmaCommonFields {

        @ParameterizedTest
        @ValueSource(strings = ["", BLANK])
        @NullSource
        fun `fails when muut is null or blank`(suunnitelma: String?) {
            val haittojenhallintasuunnitelma =
                HaittaFactory.createHaittojenhallintasuunnitelma(
                    Haittojenhallintatyyppi.MUUT to suunnitelma
                )

            val result =
                HankePublicValidator.validateHaittojenhallintasuunnitelmaCommonFields(
                    haittojenhallintasuunnitelma,
                    "hhs",
                )

            assertThat(result).failedWith("hhs.MUUT")
        }

        @ParameterizedTest
        @ValueSource(strings = ["", BLANK])
        @NullSource
        fun `fails when yleinen is null or blank`(suunnitelma: String?) {
            val haittojenhallintasuunnitelma =
                HaittaFactory.createHaittojenhallintasuunnitelma(
                    Haittojenhallintatyyppi.YLEINEN to suunnitelma
                )

            val result =
                HankePublicValidator.validateHaittojenhallintasuunnitelmaCommonFields(
                    haittojenhallintasuunnitelma,
                    "hhs",
                )

            assertThat(result).failedWith("hhs.YLEINEN")
        }
    }
}

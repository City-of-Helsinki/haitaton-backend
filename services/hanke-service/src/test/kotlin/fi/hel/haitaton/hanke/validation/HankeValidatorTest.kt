package fi.hel.haitaton.hanke.validation

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.MAXIMUM_DATE
import fi.hel.haitaton.hanke.MAXIMUM_HANKE_ALUE_NIMI_LENGTH
import fi.hel.haitaton.hanke.MAXIMUM_HANKE_NIMI_LENGTH
import fi.hel.haitaton.hanke.MAXIMUM_TYOMAAKATUOSOITE_LENGTH
import fi.hel.haitaton.hanke.SuunnitteluVaihe
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YKSITYISHENKILO
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withHankealue
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory.defaultYtunnus
import fi.hel.haitaton.hanke.factory.modify
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HankeValidatorTest {

    private val hankeValidator = HankeValidator()
    private var context: ConstraintValidatorContext = mockk(relaxUnitFun = true)
    private var violationBuilder: ConstraintViolationBuilder = mockk(relaxUnitFun = true)
    private var nodeBuilder: NodeBuilderCustomizableContext = mockk(relaxUnitFun = true)

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        every { context.buildConstraintViolationWithTemplate(any()) } returns violationBuilder
        every { violationBuilder.addConstraintViolation() } returns context
        every { violationBuilder.addPropertyNode(any()) } returns nodeBuilder
        every { nodeBuilder.addConstraintViolation() } returns context
    }

    @AfterEach
    fun checkMocks() {
        // Don't check for unnecessary stubbing. The stubs are the same for all tests.
        confirmVerified(context, violationBuilder, nodeBuilder)
    }

    @Test
    fun `fails if hanke is null`() {
        assertThat(hankeValidator.isValid(null, context)).isFalse()

        verifySequence {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
            violationBuilder.addConstraintViolation()
        }
    }

    @Test
    fun `succeeds for the default hanke`() {
        val hanke = HankeFactory.create()

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `fails if nimi is missing`() {
        val hanke = HankeFactory.create(nimi = null)

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1002, "nimi")
    }

    @Test
    fun `fails if nimi is empty`() {
        val hanke = HankeFactory.create(nimi = "")

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1002, "nimi")
    }

    @Test
    fun `succeeds if nimi is almost too long`() {
        val hanke = HankeFactory.create(nimi = "F".repeat(MAXIMUM_HANKE_NIMI_LENGTH))

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `fails if nimi is too long`() {
        val hanke = HankeFactory.create(nimi = "F".repeat(MAXIMUM_HANKE_NIMI_LENGTH + 1))

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1002, "nimi")
    }

    @Test
    fun `fails if vaihe is null`() {
        val hanke = HankeFactory.create(vaihe = null)

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1002, "vaihe")
    }

    @Test
    fun `fails if vaihe is suunnittelu and suunnitteluVaihe is null`() {
        val hanke = HankeFactory.create(vaihe = Vaihe.SUUNNITTELU, suunnitteluVaihe = null)

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1002, "suunnitteluVaihe")
    }

    @Test
    fun `succeeds if vaihe is suunnittelu and suunnitteluVaihe is defined`() {
        val hanke =
            HankeFactory.create(
                vaihe = Vaihe.SUUNNITTELU,
                suunnitteluVaihe = SuunnitteluVaihe.YLEIS_TAI_HANKE
            )

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `succeeds if tyomaaKatuosoite is null`() {
        val hanke = HankeFactory.create().apply { tyomaaKatuosoite = null }

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `succeeds if tyomaaKatuosoite is almost too long`() {
        val hanke =
            HankeFactory.create().apply {
                tyomaaKatuosoite = "F".repeat(MAXIMUM_TYOMAAKATUOSOITE_LENGTH)
            }

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `fails if tyomaaKatuosoite is too long`() {
        val hanke =
            HankeFactory.create().apply {
                tyomaaKatuosoite = "F".repeat(MAXIMUM_TYOMAAKATUOSOITE_LENGTH + 1)
            }

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1002, "tyomaaKatuosoite")
    }

    @Test
    fun `succeeds for the default hanke with an alue`() {
        val hanke = HankeFactory.create().withHankealue()

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `succeeds if hanke alue nimi is null`() {
        val hanke = HankeFactory.create().withHankealue(nimi = null)

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `succeeds if hanke alue nimi is almost too long`() {
        val hanke =
            HankeFactory.create().withHankealue(nimi = "F".repeat(MAXIMUM_HANKE_ALUE_NIMI_LENGTH))

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `fails if hanke alue nimi is null`() {
        val hanke =
            HankeFactory.create()
                .withHankealue(nimi = "F".repeat(MAXIMUM_HANKE_ALUE_NIMI_LENGTH + 1))

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1032, "alueet[0].nimi")
    }

    @Test
    fun `fails if haitta alku pvm is too far in the future`() {
        val hanke = HankeFactory.create().withHankealue(haittaAlkuPvm = MAXIMUM_DATE.plusDays(1))

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1032, "alueet[0].haittaAlkuPvm")
        verifyError(HankeError.HAI1032, "alueet[0].haittaLoppuPvm")
    }

    @Test
    fun `fails if haitta alku pvm is null`() {
        val hanke = HankeFactory.create().withHankealue(haittaAlkuPvm = null)

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1032, "alueet[0].haittaAlkuPvm")
    }

    @Test
    fun `fails if haitta loppu pvm is null`() {
        val hanke = HankeFactory.create().withHankealue(haittaLoppuPvm = null)

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1032, "alueet[0].haittaLoppuPvm")
    }

    @Test
    fun `fails if haitta loppu pvm is too far in the future`() {
        val hanke = HankeFactory.create().withHankealue(haittaLoppuPvm = MAXIMUM_DATE.plusDays(1))

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1032, "alueet[0].haittaLoppuPvm")
    }

    @Test
    fun `succeeds if haitta loppu pvm is same as start date`() {
        val hanke =
            HankeFactory.create().withHankealue(haittaLoppuPvm = DateFactory.getStartDatetime())

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `fails if haitta loppu pvm is before start date`() {
        val hanke =
            HankeFactory.create()
                .withHankealue(haittaLoppuPvm = DateFactory.getStartDatetime().minusMinutes(1))

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1032, "alueet[0].haittaLoppuPvm")
    }

    @Test
    fun `with several failing alue and a failing hanke, adds all error nodes`() {
        val hanke =
            HankeFactory.create(nimi = "")
                .withHankealue(haittaLoppuPvm = null, haittaAlkuPvm = null)
                .withHankealue(nimi = "F".repeat(101))
                .withHankealue(haittaAlkuPvm = null)
                .withHankealue(haittaLoppuPvm = DateFactory.getStartDatetime().minusMinutes(1))

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1002, "nimi")
        verifyError(HankeError.HAI1032, "alueet[0].haittaLoppuPvm")
        verifyError(HankeError.HAI1032, "alueet[0].haittaAlkuPvm")
        verifyError(HankeError.HAI1032, "alueet[1].nimi")
        verifyError(HankeError.HAI1032, "alueet[2].haittaAlkuPvm")
        verifyError(HankeError.HAI1032, "alueet[3].haittaLoppuPvm")
    }

    @Test
    fun `when ytunnus is present and valid should return ok`() {
        val hanke = HankeFactory.create().withYhteystiedot()

        val result = hankeValidator.isValid(hanke, context)

        val ytunnusCount = hanke.extractYhteystiedot().mapNotNull { it.ytunnus }.count()
        assertThat(ytunnusCount).isGreaterThanOrEqualTo(1)
        assertThat(result).isTrue()
    }

    @Test
    fun `when ytunnus is present and not valid should not return ok`() {
        val hanke =
            HankeFactory.create().withYhteystiedot().apply {
                rakennuttajat = rakennuttajat.modify(ytunnus = "1580375-3")
            }

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()
        verifyError(HankeError.HAI1002, "rakennuttajat[0].ytunnus")
    }

    @Test
    fun `when tyyppi is yksityishenkilo or null and ytunnus null should return ok`() {
        val hanke =
            HankeFactory.create().withYhteystiedot().apply {
                omistajat = omistajat.modify(ytunnus = null, tyyppi = null)
                rakennuttajat = rakennuttajat.modify(ytunnus = null, tyyppi = YKSITYISHENKILO)
                toteuttajat = toteuttajat.modify(ytunnus = null, null)
                muut = muut.modify(ytunnus = null, YKSITYISHENKILO)
            }

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `when tyyppi is yksityishenkilo and ytunnus is not null should not return ok`() {
        val hanke =
            HankeFactory.create().withYhteystiedot().apply {
                omistajat = omistajat.modify(ytunnus = defaultYtunnus, tyyppi = YKSITYISHENKILO)
            }

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()
        verifyError(HankeError.HAI1002, "omistajat[0].ytunnus")
    }

    private fun verifyError(error: HankeError, node: String) {
        verify {
            context.buildConstraintViolationWithTemplate(error.toString())
            violationBuilder.addPropertyNode(node)
            nodeBuilder.addConstraintViolation()
        }
    }
}

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
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YKSITYISHENKILO
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.HankeBuilder.Companion.toModifyRequest
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withHankealue
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory.DEFAULT_YTUNNUS
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

    private val maxHankeName = "A".repeat(MAXIMUM_HANKE_NIMI_LENGTH)
    private val maxTyomaaOsoite = "B".repeat(MAXIMUM_TYOMAAKATUOSOITE_LENGTH)
    private val maxHankealueName = "C".repeat(MAXIMUM_HANKE_ALUE_NIMI_LENGTH)

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
        val hanke = HankeFactory.create().toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `fails if nimi is empty`() {
        val hanke = HankeFactory.create(nimi = "").toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1002, "nimi")
    }

    @Test
    fun `succeeds if nimi is of max length`() {
        val hanke = HankeFactory.create(nimi = maxHankeName).toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `fails if nimi is too long`() {
        val hanke = HankeFactory.create(nimi = maxHankeName + "X").toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1002, "nimi")
    }

    @Test
    fun `succeeds if tyomaaKatuosoite is null`() {
        val hanke = HankeFactory.create().apply { tyomaaKatuosoite = null }.toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `succeeds if tyomaaKatuosoite is almost too long`() {
        val hanke =
            HankeFactory.create().apply { tyomaaKatuosoite = maxTyomaaOsoite }.toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `fails if tyomaaKatuosoite is too long`() {
        val hanke =
            HankeFactory.create()
                .apply { tyomaaKatuosoite = maxTyomaaOsoite + "X" }
                .toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1002, "tyomaaKatuosoite")
    }

    @Test
    fun `succeeds for the default hanke with an alue`() {
        val hanke = HankeFactory.create().withHankealue().toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `fails if hanke alue nimi is empty`() {
        val hanke = HankeFactory.create().withHankealue(nimi = "").toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1032, "alueet[0].nimi")
    }

    @Test
    fun `succeeds if hanke alue nimi is almost too long`() {
        val hanke = HankeFactory.create().withHankealue(nimi = maxHankealueName).toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `fails if hanke alue nimi is too long`() {
        val hanke =
            HankeFactory.create().withHankealue(nimi = maxHankealueName + "X").toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1032, "alueet[0].nimi")
    }

    @Test
    fun `fails if haitta alku pvm is too far in the future`() {
        val hanke =
            HankeFactory.create()
                .withHankealue(haittaAlkuPvm = MAXIMUM_DATE.plusDays(1))
                .toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1032, "alueet[0].haittaAlkuPvm")
        verifyError(HankeError.HAI1032, "alueet[0].haittaLoppuPvm")
    }

    @Test
    fun `fails if haitta loppu pvm is too far in the future`() {
        val hanke =
            HankeFactory.create()
                .withHankealue(haittaLoppuPvm = MAXIMUM_DATE.plusDays(1))
                .toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1032, "alueet[0].haittaLoppuPvm")
    }

    @Test
    fun `succeeds if haitta loppu pvm is same as start date`() {
        val hanke =
            HankeFactory.create()
                .withHankealue(haittaLoppuPvm = DateFactory.getStartDatetime())
                .toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `fails if haitta loppu pvm is before start date`() {
        val hanke =
            HankeFactory.create()
                .withHankealue(haittaLoppuPvm = DateFactory.getStartDatetime().minusMinutes(1))
                .toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1032, "alueet[0].haittaLoppuPvm")
    }

    @Test
    fun `with several failures, adds all error nodes`() {
        val hanke =
            HankeFactory.create(nimi = "")
                .withHankealue(nimi = maxHankealueName + "X")
                .withHankealue(haittaLoppuPvm = MAXIMUM_DATE.plusDays(1))
                .toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()

        verifyError(HankeError.HAI1002, "nimi")
        verifyError(HankeError.HAI1032, "alueet[0].nimi")
        verifyError(HankeError.HAI1032, "alueet[1].haittaLoppuPvm")
    }

    @Test
    fun `when ytunnus is present and valid should return ok`() {
        val hanke = HankeFactory.create().withYhteystiedot().toModifyRequest()

        val result = hankeValidator.isValid(hanke, context)

        val ytunnusCount = hanke.extractYhteystiedot().mapNotNull { it.ytunnus }.count()
        assertThat(ytunnusCount).isGreaterThanOrEqualTo(1)
        assertThat(result).isTrue()
    }

    @Test
    fun `when ytunnus is present and not valid should not return ok`() {
        val hanke =
            HankeFactory.create()
                .withYhteystiedot()
                .apply { rakennuttajat = rakennuttajat.modify(ytunnus = "1580375-3") }
                .toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isFalse()
        verifyError(HankeError.HAI1002, "rakennuttajat[0].ytunnus")
    }

    @Test
    fun `when tyyppi is yksityishenkilo or null and ytunnus null should return ok`() {
        val hanke =
            HankeFactory.create()
                .withYhteystiedot()
                .apply {
                    omistajat = omistajat.modify(ytunnus = null, tyyppi = null)
                    rakennuttajat = rakennuttajat.modify(ytunnus = null, tyyppi = YKSITYISHENKILO)
                    toteuttajat = toteuttajat.modify(ytunnus = null, null)
                    muut = muut.modify(ytunnus = null, YKSITYISHENKILO)
                }
                .toModifyRequest()

        assertThat(hankeValidator.isValid(hanke, context)).isTrue()
    }

    @Test
    fun `when tyyppi is yksityishenkilo and ytunnus is not null should not return ok`() {
        val hanke =
            HankeFactory.create()
                .withYhteystiedot()
                .apply {
                    omistajat =
                        omistajat.modify(ytunnus = DEFAULT_YTUNNUS, tyyppi = YKSITYISHENKILO)
                }
                .toModifyRequest()

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

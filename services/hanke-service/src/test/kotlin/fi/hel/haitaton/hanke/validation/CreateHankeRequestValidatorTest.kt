package fi.hel.haitaton.hanke.validation

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.MAXIMUM_HANKE_NIMI_LENGTH
import fi.hel.haitaton.hanke.factory.HankeFactory
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import jakarta.validation.ConstraintValidatorContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CreateHankeRequestValidatorTest {
    private val validator = CreateHankeRequestValidator()
    private var context: ConstraintValidatorContext = mockk(relaxUnitFun = true)
    private var violationBuilder: ConstraintValidatorContext.ConstraintViolationBuilder =
        mockk(relaxUnitFun = true)
    private var nodeBuilder:
        ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext =
        mockk(relaxUnitFun = true)

    private val maxHankeName = "A".repeat(MAXIMUM_HANKE_NIMI_LENGTH)

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
    fun `fails if request is null`() {
        assertThat(validator.isValid(null, context)).isFalse()

        verifySequence {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
            violationBuilder.addConstraintViolation()
        }
    }

    @Test
    fun `succeeds for the default request`() {
        val request = HankeFactory.createRequest()

        assertThat(validator.isValid(request, context)).isTrue()
    }

    @Test
    fun `fails if nimi is empty`() {
        val request = HankeFactory.createRequest(nimi = "")

        assertThat(validator.isValid(request, context)).isFalse()

        verifyError("nimi")
    }

    @Test
    fun `fails if nimi is blank`() {
        val request = HankeFactory.createRequest(nimi = "  ")

        assertThat(validator.isValid(request, context)).isFalse()

        verifyError("nimi")
    }

    @Test
    fun `succeeds if nimi is of max length`() {
        val request = HankeFactory.createRequest(nimi = maxHankeName)

        assertThat(validator.isValid(request, context)).isTrue()
    }

    @Test
    fun `fails if nimi is too long`() {
        val request = HankeFactory.createRequest(nimi = maxHankeName + "X")

        assertThat(validator.isValid(request, context)).isFalse()

        verifyError("nimi")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `fails if perustaja sahkoposti is invalid`(sahkoposti: String) {
        val perustaja = HankeFactory.DEFAULT_HANKE_PERUSTAJA.copy(sahkoposti = sahkoposti)
        val request = HankeFactory.createRequest(perustaja = perustaja)

        assertThat(validator.isValid(request, context)).isFalse()

        verifyError("perustaja.sahkoposti")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `fails if perustaja puhelinnumero is invalid`(puhelinnumero: String) {
        val perustaja = HankeFactory.DEFAULT_HANKE_PERUSTAJA.copy(puhelinnumero = puhelinnumero)
        val request = HankeFactory.createRequest(perustaja = perustaja)

        assertThat(validator.isValid(request, context)).isFalse()

        verifyError("perustaja.puhelinnumero")
    }

    private fun verifyError(node: String) {
        verify {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
            violationBuilder.addPropertyNode(node)
            nodeBuilder.addConstraintViolation()
        }
    }
}

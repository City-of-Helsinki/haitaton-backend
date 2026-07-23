package fi.hel.haitaton.hanke.logging

import assertk.all
import assertk.assertFailure
import assertk.assertions.hasClass
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.ProfiiliFactory
import fi.hel.haitaton.hanke.hakemus.HakemusDeletionResultDto
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder

class DisclosureLoggingAspectTest {

    private val disclosureLogService: DisclosureLogService = mockk(relaxUnitFun = true)
    private val disclosureLoggingAspect = DisclosureLoggingAspect(disclosureLogService)

    @BeforeEach
    fun cleanup() {
        clearAllMocks()
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(disclosureLogService)
        SecurityContextHolder.clearContext()
    }

    @Nested
    inner class LogResponse {

        private fun mockAuthentication() {
            val authentication: Authentication = mockk()
            every { authentication.name } returns USERNAME
            val context: SecurityContext = mockk()
            every { context.authentication } returns authentication
            SecurityContextHolder.setContext(context)
        }

        @Test
        fun `does nothing when called with null`() {
            disclosureLoggingAspect.logResponse(null)
        }

        @Test
        fun `throws an exception when called with an unspecified type`() {
            val failure = assertFailure { disclosureLoggingAspect.logResponse(5.toShort()) }

            failure.all {
                hasClass(UnknownResponseTypeException::class)
                messageContains("Unknown response type")
                messageContains("kotlin.Short")
            }
        }

        @Test
        fun `does nothing when called with a type that just returns`() {
            disclosureLoggingAspect.logResponse(HakemusDeletionResultDto(false))
        }

        @Test
        fun `calls disclosure service for logging when necessary`() {
            mockAuthentication()

            disclosureLoggingAspect.logResponse(ProfiiliFactory.DEFAULT_NAMES)

            verifySequence {
                disclosureLogService.saveForProfiiliNimi(ProfiiliFactory.DEFAULT_NAMES, USERNAME)
            }
        }

        @Test
        fun `does nothing when called with an empty list`() {
            disclosureLoggingAspect.logResponse(listOf<Hanke>())
        }

        @Test
        fun `throws an exception when called with a list of unknown types`() {
            val failure = assertFailure { disclosureLoggingAspect.logResponse(listOf<Short>(4)) }

            failure.all {
                hasClass(UnknownResponseListTypeException::class)
                messageContains("Unknown response type inside a list")
                messageContains("kotlin.Short")
            }
        }

        @Test
        fun `throws an exception when called with a list of mixed types`() {
            val list = listOf(HankeFactory.create(), "", "", 3, 4, "other string")

            val failure = assertFailure { disclosureLoggingAspect.logResponse(list) }

            failure.all {
                hasClass(MixedElementsInResponseException::class)
                messageContains("Mixed types inside a list")
                messageContains("Expected type: fi.hel.haitaton.hanke.domain.Hanke")
                messageContains(
                    "Actual types: fi.hel.haitaton.hanke.domain.Hanke, kotlin.String, kotlin.Int"
                )
            }
        }

        @Test
        fun `does nothing when response is a list of types that don't need logging`() {
            disclosureLoggingAspect.logResponse(listOf(""))
        }

        @Test
        fun `does nothing when response is a list where first element is not logged`() {
            disclosureLoggingAspect.logResponse(listOf("", HankeFactory.create()))
        }

        @Test
        fun `calls disclosure service for lists when list has loggable types`() {
            mockAuthentication()
            val list = listOf(HankeFactory.create(), HankeFactory.create(4))

            disclosureLoggingAspect.logResponse(list)

            verifySequence { disclosureLogService.saveForHankkeet(list, USERNAME) }
        }

        @Test
        fun `calls disclosure service when response is a ResponseEntity wrapping a logged type`() {
            mockAuthentication()

            disclosureLoggingAspect.logResponse(
                ResponseEntity.ofNullable(ProfiiliFactory.DEFAULT_NAMES)
            )

            verifySequence {
                disclosureLogService.saveForProfiiliNimi(ProfiiliFactory.DEFAULT_NAMES, USERNAME)
            }
        }

        @Test
        fun `does nothing when called with an empty map`() {
            disclosureLoggingAspect.logResponse(mapOf<Int, Hanke>())
        }

        @Test
        fun `throws an exception when called with a map of unknown values`() {
            val failure = assertFailure {
                disclosureLoggingAspect.logResponse(mapOf<Int, Short>(4 to 4))
            }

            failure.all {
                hasClass(UnknownResponseListTypeException::class)
                messageContains("Unknown response type inside a list")
                messageContains("kotlin.Short")
            }
        }

        @Test
        fun `throws an exception when called with a map of mixed type values`() {
            val list =
                listOf(HankeFactory.create(), "", "", 3, 4, "other string")
                    .mapIndexed { i, v -> i to v }
                    .toMap()

            val failure = assertFailure { disclosureLoggingAspect.logResponse(list) }

            failure.all {
                hasClass(MixedElementsInResponseException::class)
                messageContains("Mixed types inside a list")
                messageContains("Expected type: fi.hel.haitaton.hanke.domain.Hanke")
                messageContains(
                    "Actual types: fi.hel.haitaton.hanke.domain.Hanke, kotlin.String, kotlin.Int"
                )
            }
        }

        @Test
        fun `does nothing when response is a map with values of types that don't need logging`() {
            disclosureLoggingAspect.logResponse(mapOf(1 to ""))
        }

        @Test
        fun `does nothing when response is a map where first value is not logged`() {
            disclosureLoggingAspect.logResponse(mapOf(1 to "", 2 to HankeFactory.create()))
        }

        @Test
        fun `calls disclosure service with values when map has values with loggable types`() {
            mockAuthentication()
            val map = mapOf(1 to HankeFactory.create(), 2 to HankeFactory.create(4))

            disclosureLoggingAspect.logResponse(map)

            verifySequence { disclosureLogService.saveForHankkeet(map.values.toList(), USERNAME) }
        }
    }
}

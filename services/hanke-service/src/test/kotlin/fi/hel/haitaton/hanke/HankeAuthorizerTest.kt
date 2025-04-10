package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.factory.TestHankeIdentifier
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder

class HankeAuthorizerTest {

    private val permissionService: PermissionService = mockk()
    private val hankeRepository: HankeRepository = mockk()
    private val authorizer: HankeAuthorizer = HankeAuthorizer(permissionService, hankeRepository)

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
        mockAuthentication()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(permissionService, hankeRepository)
    }

    private fun mockAuthentication() {
        val authentication: Authentication = mockk()
        every { authentication.name } returns USERNAME
        val context: SecurityContext = mockk()
        every { context.authentication } returns authentication
        SecurityContextHolder.setContext(context)
    }

    @Nested
    inner class AuthorizeHankeTunnus {
        val hankeId = 6532
        val hanketunnus = "HAI24-141"

        @Test
        fun `returns true when viewing a completed hanke`() {
            every { hankeRepository.findOneByHankeTunnus(hanketunnus) } returns
                TestHankeIdentifier(hankeId, hanketunnus)
            every {
                permissionService.hasPermission(hankeId, USERNAME, PermissionCode.VIEW)
            } returns true
            every { hankeRepository.findStatusById(hankeId) } returns HankeStatus.COMPLETED

            val result = authorizer.authorizeHankeTunnus(hanketunnus, PermissionCode.VIEW)

            assertThat(result).isTrue()
            verifySequence {
                hankeRepository.findOneByHankeTunnus(hanketunnus)
                permissionService.hasPermission(hankeId, USERNAME, PermissionCode.VIEW)
                hankeRepository.findStatusById(hankeId)
            }
        }

        @ParameterizedTest
        @EnumSource(PermissionCode::class, names = ["VIEW"], mode = EnumSource.Mode.EXCLUDE)
        fun `throws exception when modifying a completed hanke`(permission: PermissionCode) {
            every { hankeRepository.findOneByHankeTunnus(hanketunnus) } returns
                TestHankeIdentifier(hankeId, hanketunnus)
            every { permissionService.hasPermission(hankeId, USERNAME, permission) } returns true
            every { hankeRepository.findStatusById(hankeId) } returns HankeStatus.COMPLETED

            val failure = assertFailure { authorizer.authorizeHankeTunnus(hanketunnus, permission) }

            failure.all {
                hasClass(HankeAlreadyCompletedException::class.java)
                messageContains("Hanke has already been completed, so the operation is not allowed")
                messageContains("hankeId=$hankeId")
            }
            verifySequence {
                hankeRepository.findOneByHankeTunnus(hanketunnus)
                permissionService.hasPermission(hankeId, USERNAME, permission)
                hankeRepository.findStatusById(hankeId)
            }
        }
    }
}

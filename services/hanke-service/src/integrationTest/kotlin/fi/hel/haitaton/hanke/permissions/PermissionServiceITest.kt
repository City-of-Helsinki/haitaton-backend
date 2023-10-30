package fi.hel.haitaton.hanke.permissions

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.first
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.logging.AuditLogEvent
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.auditEvent
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasId
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectBefore
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.withTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

private const val CURRENT_USER: String = "test7358"

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@WithMockUser(username = CURRENT_USER)
class PermissionServiceITest : DatabaseTest() {

    val username = "user"

    @Autowired lateinit var permissionService: PermissionService
    @Autowired lateinit var permissionRepository: PermissionRepository
    @Autowired lateinit var hankeService: HankeService
    @Autowired lateinit var hankeFactory: HankeFactory
    @Autowired lateinit var auditLogRepository: AuditLogRepository

    @Nested
    inner class AllowedHankeIds {
        @Test
        fun `Without permissions returns empty list`() {
            val response = permissionService.getAllowedHankeIds(username, PermissionCode.EDIT)

            assertEquals(listOf<Int>(), response)
        }

        @Test
        fun `With permissions returns list of IDs`() {
            val hankkeet = hankeFactory.saveSeveralMinimal(3)
            hankkeet
                .map { it.id }
                .forEach {
                    permissionService.create(
                        userId = username,
                        hankeId = it,
                        kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET,
                    )
                }

            val response = permissionService.getAllowedHankeIds(username, PermissionCode.EDIT)

            assertEquals(hankkeet.map { it.id }, response)
        }

        @Test
        fun `Returns ids with correct permissions`() {
            val kaikkiOikeudet = Kayttooikeustaso.KAIKKI_OIKEUDET
            val hankemuokkaus = Kayttooikeustaso.HANKEMUOKKAUS
            val hakemusasiointi = Kayttooikeustaso.HAKEMUSASIOINTI
            val katseluoikeus = Kayttooikeustaso.KATSELUOIKEUS
            val hankkeet = hankeFactory.saveSeveralMinimal(4)
            listOf(kaikkiOikeudet, hankemuokkaus, hakemusasiointi, katseluoikeus).zip(hankkeet) {
                kayttooikeustaso,
                hanke ->
                permissionService.create(hanke.id, username, kayttooikeustaso)
            }

            val response = permissionService.getAllowedHankeIds(username, PermissionCode.EDIT)

            assertEquals(listOf(hankkeet[0].id, hankkeet[1].id), response)
        }
    }

    @Nested
    inner class PermissionsByHanke {
        private val canView = Kayttooikeustaso.KATSELUOIKEUS
        private val allRights = Kayttooikeustaso.KAIKKI_OIKEUDET

        @Test
        fun `Should return empty if no permissions`() {
            val result = permissionService.permissionsByHanke(CURRENT_USER)

            assertThat(result).isEmpty()
        }

        @Test
        fun `Should find all users permissions`() {
            val firstHanke = hankeFactory.saveGenerated(userId = CURRENT_USER)
            val secondHanke = hankeFactory.saveMinimal().permit(privilege = canView)
            hankeFactory.saveMinimal().permit(username) // someone else

            val result = permissionService.permissionsByHanke(CURRENT_USER)

            assertThat(result).hasSize(2)
            assertThat(result.find { it.hankeTunnus == firstHanke.hankeTunnus }).isNotNull().all {
                prop(HankePermission::hankeKayttajaId).isNotNull()
                prop(HankePermission::kayttooikeustaso).isEqualTo(allRights)
                prop(HankePermission::permissionCode).isEqualTo(1152921504606846975)
                prop(HankePermission::permissionCodes).hasSameElementsAs(PermissionCode.entries)
            }
            assertThat(result.find { it.hankeTunnus == secondHanke.hankeTunnus }).isNotNull().all {
                prop(HankePermission::hankeKayttajaId).isNull()
                prop(HankePermission::kayttooikeustaso).isEqualTo(canView)
                prop(HankePermission::permissionCode).isEqualTo(1)
                prop(HankePermission::permissionCodes).containsExactly(PermissionCode.VIEW)
            }
        }

        private fun HankeEntity.permit(
            userId: String = CURRENT_USER,
            privilege: Kayttooikeustaso = allRights,
        ) = also { permissionService.create(id, userId, privilege) }
    }

    @Nested
    inner class HasPermission {
        @Test
        fun `hasPermission returns false without permissions`() {
            assertFalse(permissionService.hasPermission(2, username, PermissionCode.EDIT))
        }

        @Test
        fun `hasPermission with correct permission`() {
            val hankeId = hankeFactory.saveMinimal().id
            permissionService.create(hankeId, username, Kayttooikeustaso.KAIKKI_OIKEUDET)

            assertTrue(permissionService.hasPermission(hankeId, username, PermissionCode.EDIT))
        }

        @Test
        fun `hasPermission with insufficient permissions`() {
            val hankeId = hankeFactory.saveMinimal().id
            permissionService.create(hankeId, username, Kayttooikeustaso.HAKEMUSASIOINTI)

            assertFalse(permissionService.hasPermission(hankeId, username, PermissionCode.EDIT))
        }
    }

    @Nested
    inner class Create {

        @Test
        fun `Creates a new permission`() {
            val hankeId = hankeFactory.saveMinimal().id

            val result = permissionService.create(hankeId, username, Kayttooikeustaso.KATSELUOIKEUS)

            val permissions = permissionRepository.findAll()
            assertThat(permissions).hasSize(1)
            assertThat(permissions).first().all {
                prop(PermissionEntity::id).isEqualTo(result.id)
                prop(PermissionEntity::hankeId).isEqualTo(hankeId)
                prop(PermissionEntity::userId).isEqualTo(username)
                prop(PermissionEntity::kayttooikeustaso).isEqualTo(Kayttooikeustaso.KATSELUOIKEUS)
            }
        }

        @Test
        fun `Writes to audit log`() {
            val hankeId = hankeFactory.saveMinimal().id
            assertThat(auditLogRepository.findAll()).isEmpty()

            val permission =
                permissionService.create(hankeId, username, Kayttooikeustaso.KATSELUOIKEUS)

            val logs = auditLogRepository.findAll()
            assertThat(logs).hasSize(1)
            val expectedObject =
                Permission(
                    id = permission.id,
                    userId = username,
                    hankeId = hankeId,
                    kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
                )
            assertThat(logs).first().auditEvent {
                withTarget {
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.PERMISSION)
                    hasId(permission.id)
                    prop(AuditLogTarget::objectBefore).isNull()
                    hasObjectAfter(expectedObject)
                }
                prop(AuditLogEvent::operation).isEqualTo(Operation.CREATE)
                hasUserActor(CURRENT_USER)
            }
        }
    }

    @Nested
    inner class UpdateKayttooikeustaso {

        @Test
        fun `updateKayttooikeustaso updates an existing permission`() {
            val hankeId = hankeFactory.saveMinimal().id
            val permission =
                permissionService.create(hankeId, username, Kayttooikeustaso.KATSELUOIKEUS)

            permissionService.updateKayttooikeustaso(
                permission,
                Kayttooikeustaso.HAKEMUSASIOINTI,
                username,
            )

            val permissions = permissionRepository.findAll()
            assertThat(permissions).hasSize(1)
            assertThat(permissions).first().all {
                prop(PermissionEntity::id).isEqualTo(permission.id)
                prop(PermissionEntity::hankeId).isEqualTo(hankeId)
                prop(PermissionEntity::userId).isEqualTo(username)
                prop(PermissionEntity::kayttooikeustaso).isEqualTo(Kayttooikeustaso.HAKEMUSASIOINTI)
            }
        }

        @Test
        fun `updateKayttooikeustaso writes to audit log`() {
            val hankeId = hankeFactory.saveMinimal().id
            val permission =
                permissionService.create(hankeId, username, Kayttooikeustaso.KATSELUOIKEUS)
            auditLogRepository.deleteAll()

            permissionService.updateKayttooikeustaso(
                permission,
                Kayttooikeustaso.HAKEMUSASIOINTI,
                CURRENT_USER,
            )

            val logs = auditLogRepository.findAll()
            assertThat(logs).hasSize(1)
            val expectedObject =
                Permission(
                    id = permission.id,
                    userId = username,
                    hankeId = hankeId,
                    kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
                )
            assertThat(logs).first().isSuccess(Operation.UPDATE) {
                withTarget {
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.PERMISSION)
                    hasId(permission.id)
                    hasObjectBefore(expectedObject)
                    hasObjectAfter(
                        expectedObject.copy(kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI)
                    )
                }
                hasUserActor(CURRENT_USER)
            }
        }
    }
}

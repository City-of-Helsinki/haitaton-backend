package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.domain.HankeReminder
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.email.textBody
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankealueFactory
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.HAITATON_AUDIT_LOG_USERID
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasId
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasNoObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasServiceActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.withTarget
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class HankeCompletionServiceITest(
    @Autowired private val hankeCompletionService: HankeCompletionService,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val hankeAttachmentFactory: HankeAttachmentFactory,
    @Autowired private val auditLogRepository: AuditLogRepository,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hakemusRepository: HakemusRepository,
    @Autowired private val hankeRepository: HankeRepository,
    @Autowired private val fileClient: MockFileClient,
) : IntegrationTest() {

    @Nested
    inner class GetPublicIds {

        @Test
        fun `with no hanke returns empty list`() {
            val result = hankeCompletionService.getPublicIds()

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns only public hanke`() {
            hankeFactory.builder().saveEntity(HankeStatus.DRAFT)
            hankeFactory.builder().saveEntity(HankeStatus.COMPLETED)
            val publicHanke = hankeFactory.builder().saveEntity(HankeStatus.PUBLIC)

            val result = hankeCompletionService.getPublicIds()

            assertThat(result).containsExactly(publicHanke.id)
        }

        @Test
        fun `only returns hanke where the last alue end date is in the past`() {
            val pastHanke =
                hankeFactory
                    .builder()
                    .withHankealue(
                        HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().minusDays(1))
                    )
                    .saveEntity(HankeStatus.PUBLIC)
            hankeFactory
                .builder()
                .withHankealue(HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now()))
                .saveEntity(HankeStatus.PUBLIC)
            hankeFactory
                .builder()
                .withHankealue(
                    HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().plusDays(1))
                )
                .saveEntity(HankeStatus.PUBLIC)

            val result = hankeCompletionService.getPublicIds()

            assertThat(result).containsExactly(pastHanke.id)
        }

        @Test
        fun `returns ids for hanke with no areas and with areas without an end date`() {
            val hankeWithoutArea =
                hankeFactory.builder().withNoAreas().saveEntity(HankeStatus.PUBLIC)
            val hankeWithoutEndDate =
                hankeFactory
                    .builder()
                    .withHankealue(HankealueFactory.create(haittaLoppuPvm = null))
                    .saveEntity(HankeStatus.PUBLIC)

            val result = hankeCompletionService.getPublicIds()

            assertThat(result)
                .containsExactlyInAnyOrder(hankeWithoutArea.id, hankeWithoutEndDate.id)
        }

        @Test
        fun `returns only the the first n ids, ordered by modifiedAt`() {
            val baseDate = LocalDateTime.parse("2025-03-01T09:54:05")
            val hankkeet =
                listOf(30, 2, 25, null, 15, 4).map { date ->
                    val hanke = hankeFactory.builder().saveEntity(HankeStatus.PUBLIC)
                    hanke.modifiedAt = date?.let { baseDate.withDayOfMonth(it) }
                    hankeRepository.save(hanke)
                }

            val result = hankeCompletionService.getPublicIds()

            // max-per-run is set to 3 in application-test.yml
            assertThat(result).containsExactly(hankkeet[1].id, hankkeet[5].id, hankkeet[4].id)
        }
    }

    @Nested
    inner class IdsForReminders {
        @Test
        fun `with no hanke returns empty list`() {
            val result = hankeCompletionService.idsForReminders(HankeReminder.COMPLETION_5)

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns only public hanke`() {
            hankeFactory.builder().saveEntity(HankeStatus.DRAFT)
            hankeFactory.builder().saveEntity(HankeStatus.COMPLETED)
            val publicHanke = hankeFactory.builder().saveEntity(HankeStatus.PUBLIC)

            val result = hankeCompletionService.idsForReminders(HankeReminder.COMPLETION_14)

            assertThat(result).containsExactly(publicHanke.id)
        }

        @Test
        fun `returns ids for hanke with no areas without an end date`() {
            val hankeWithoutArea =
                hankeFactory.builder().withNoAreas().saveEntity(HankeStatus.PUBLIC)
            val hankeWithoutEndDate =
                hankeFactory
                    .builder()
                    .withHankealue(HankealueFactory.create(haittaLoppuPvm = null))
                    .saveEntity(HankeStatus.PUBLIC)

            val result = hankeCompletionService.idsForReminders(HankeReminder.COMPLETION_14)

            assertThat(result)
                .containsExactlyInAnyOrder(hankeWithoutArea.id, hankeWithoutEndDate.id)
        }

        @Test
        fun `returns only the the first n ids, ordered by modifiedAt`() {
            val baseDate = LocalDateTime.parse("2025-03-01T09:54:05")
            val hankkeet =
                listOf(30, 2, 25, null, 15, 4).map { date ->
                    val hanke = hankeFactory.builder().saveEntity(HankeStatus.PUBLIC)
                    hanke.modifiedAt = date?.let { baseDate.withDayOfMonth(it) }
                    hankeRepository.save(hanke)
                }

            val result = hankeCompletionService.idsForReminders(HankeReminder.COMPLETION_14)

            // max-per-run is set to 3 in application-test.yml
            assertThat(result).containsExactly(hankkeet[1].id, hankkeet[5].id, hankkeet[4].id)
        }

        @Test
        fun `only returns hanke where the reminder is due`() {
            val justBeforeHanke =
                hankeFactory
                    .builder()
                    .withHankealue(
                        HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().plusDays(4))
                    )
                    .saveEntity(HankeStatus.PUBLIC)
            val onTheDayHanke =
                hankeFactory
                    .builder()
                    .withHankealue(
                        HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().plusDays(5))
                    )
                    .saveEntity(HankeStatus.PUBLIC)
            hankeFactory
                .builder()
                .withHankealue(
                    HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().plusDays(6))
                )
                .saveEntity(HankeStatus.PUBLIC)

            val result = hankeCompletionService.idsForReminders(HankeReminder.COMPLETION_5)

            assertThat(result).containsExactly(justBeforeHanke.id, onTheDayHanke.id)
        }

        @Test
        fun `returns only hanke where the reminder hasn't been sent already`() {
            val unsentHanke =
                hankeFactory
                    .builder()
                    .withHankealue(
                        HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().plusDays(5))
                    )
                    .saveEntity(HankeStatus.PUBLIC) { it.sentReminders = arrayOf() }
            hankeFactory
                .builder()
                .withHankealue(
                    HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().plusDays(5))
                )
                .saveEntity(HankeStatus.PUBLIC) {
                    it.sentReminders = arrayOf(HankeReminder.COMPLETION_5)
                }

            val result = hankeCompletionService.idsForReminders(HankeReminder.COMPLETION_5)

            assertThat(result).containsExactly(unsentHanke.id)
        }
    }

    @Nested
    inner class CompleteHankeIfPossible {
        private fun baseHanke() =
            hankeFactory
                .builder()
                .withHankealue(
                    HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().minusDays(1))
                )

        @Test
        fun `throws exception when the hanke is not public`() {
            val hanke = baseHanke().saveEntity(HankeStatus.COMPLETED)

            val failure = assertFailure { hankeCompletionService.completeHankeIfPossible(hanke.id) }

            failure.hasClass(HankeNotPublicException::class)
        }

        @Test
        fun `throws exception when the hanke has no areas`() {
            val hanke = baseHanke().withNoAreas().saveEntity(HankeStatus.PUBLIC)

            val failure = assertFailure { hankeCompletionService.completeHankeIfPossible(hanke.id) }

            failure.hasClass(PublicHankeHasNoAreasException::class)
        }

        @Test
        fun `throws exception when the hanke has an empty end date`() {
            val hanke =
                baseHanke()
                    .withHankealue(HankealueFactory.create(haittaLoppuPvm = null))
                    .saveEntity(HankeStatus.PUBLIC)

            val failure = assertFailure { hankeCompletionService.completeHankeIfPossible(hanke.id) }

            failure.hasClass(HankealueWithoutEndDateException::class)
        }

        @Test
        fun `doesn't change hanke status when hanke has areas in the future`() {
            val hanke =
                baseHanke()
                    .withHankealue(
                        HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().plusDays(1))
                    )
                    .saveEntity(HankeStatus.PUBLIC)

            hankeCompletionService.completeHankeIfPossible(hanke.id)

            val result = hankeRepository.getReferenceById(hanke.id)
            assertThat(result.status).isEqualTo(HankeStatus.PUBLIC)
            assertThat(result.completedAt).isNull()
        }

        @Test
        fun `doesn't change hanke status when hanke has active application`() {
            val hanke = baseHanke().saveEntity(HankeStatus.PUBLIC)
            hakemusFactory
                .builder(hanke)
                .withMandatoryFields()
                .withStatus(status = ApplicationStatus.HANDLING)
                .saveEntity()

            hankeCompletionService.completeHankeIfPossible(hanke.id)

            val result = hankeRepository.getReferenceById(hanke.id)
            assertThat(result.status).isEqualTo(HankeStatus.PUBLIC)
            assertThat(result.completedAt).isNull()
        }

        @Test
        fun `changes hanke status when hanke has archived application`() {
            val hanke = baseHanke().saveEntity(HankeStatus.PUBLIC)
            hakemusFactory
                .builder(hanke)
                .withMandatoryFields()
                .withStatus(status = ApplicationStatus.ARCHIVED)
                .saveEntity()

            hankeCompletionService.completeHankeIfPossible(hanke.id)

            val result = hankeRepository.getReferenceById(hanke.id)
            assertThat(result.status).isEqualTo(HankeStatus.COMPLETED)
            assertThat(result.completedAt).isRecent()
        }
    }

    @Nested
    inner class SendReminderIfNecessary {
        @Test
        fun `throws exception when the hanke is not public`() {
            val hanke = hankeFactory.builder().saveEntity(HankeStatus.COMPLETED)

            val failure = assertFailure {
                hankeCompletionService.sendReminderIfNecessary(hanke.id, HankeReminder.COMPLETION_5)
            }

            failure.hasClass(HankeNotPublicException::class)
        }

        @Test
        fun `throws exception when the hanke has no areas`() {
            val hanke = hankeFactory.builder().withNoAreas().saveEntity(HankeStatus.PUBLIC)

            val failure = assertFailure {
                hankeCompletionService.sendReminderIfNecessary(hanke.id, HankeReminder.COMPLETION_5)
            }

            failure.hasClass(PublicHankeHasNoAreasException::class)
        }

        @Test
        fun `throws exception when the hanke has only empty end dates`() {
            val hanke =
                hankeFactory
                    .builder()
                    .withHankealue(HankealueFactory.create(haittaLoppuPvm = null))
                    .saveEntity(HankeStatus.PUBLIC)

            val failure = assertFailure {
                hankeCompletionService.sendReminderIfNecessary(
                    hanke.id,
                    HankeReminder.COMPLETION_14,
                )
            }

            failure.hasClass(HankealueWithoutEndDateException::class)
        }

        @ParameterizedTest
        @EnumSource(HankeReminder::class, names = ["COMPLETION_5", "COMPLETION_14"])
        fun `marks the reminder sent but doesn't send anything when the hanke is due to be completed`(
            reminder: HankeReminder
        ) {
            val hanke =
                hankeFactory
                    .builder()
                    .withHankealue(HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now()))
                    .saveEntity(HankeStatus.PUBLIC)

            hankeCompletionService.sendReminderIfNecessary(hanke.id, reminder)

            val updatedHanke = hankeRepository.getReferenceById(hanke.id)
            assertThat(updatedHanke.sentReminders).containsExactly(reminder)
            assertThat(greenMail.receivedMessages).isEmpty()
        }

        @Test
        fun `sends the reminder to everyone with EDIT permissions`() {
            val hanke =
                hankeFactory
                    .builder()
                    .withHankealue(
                        HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().plusDays(5))
                    )
                    .saveWithYhteystiedot {
                        omistaja(
                            kayttaja("omistaja@test", "omistaja", Kayttooikeustaso.HANKEMUOKKAUS)
                        )
                        rakennuttaja(
                            kayttaja(
                                "rakennuttaja@test",
                                "rakennuttaja",
                                Kayttooikeustaso.HAKEMUSASIOINTI,
                            )
                        )
                        toteuttaja(
                            kayttaja(
                                "toteuttaja@test",
                                "toteuttaja",
                                Kayttooikeustaso.KAIKKIEN_MUOKKAUS,
                            )
                        )
                    }
            hanke.status = HankeStatus.PUBLIC
            hankeRepository.save(hanke)

            hankeCompletionService.sendReminderIfNecessary(hanke.id, HankeReminder.COMPLETION_5)

            assertThat(greenMail.receivedMessages).hasSize(3)
            val recipients = greenMail.receivedMessages.map { it.allRecipients.single().toString() }
            assertThat(recipients).hasSize(3)
            assertThat(recipients)
                .containsExactlyInAnyOrder(
                    "pertti@perustaja.test",
                    "omistaja@test",
                    "toteuttaja@test",
                )
        }

        @Test
        fun `uses the last end date of the hankealue when determining the hanke end date`() {
            val hanke =
                hankeFactory
                    .builder()
                    .withHankealue(HankealueFactory.create(haittaLoppuPvm = null))
                    .withHankealue(
                        HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().plusDays(5))
                    )
                    .withHankealue(
                        HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().plusDays(6))
                    )
                    .saveEntity(HankeStatus.PUBLIC)

            hankeCompletionService.sendReminderIfNecessary(hanke.id, HankeReminder.COMPLETION_5)

            val updatedHanke = hankeRepository.getReferenceById(hanke.id)
            assertThat(updatedHanke.sentReminders).isEmpty()
            assertThat(greenMail.receivedMessages).isEmpty()
        }

        @Nested
        inner class Completion5 {
            @Test
            fun `does nothing if the reminder is not yet due`() {
                val hanke =
                    hankeFactory
                        .builder()
                        .withHankealue(
                            HankealueFactory.create(
                                haittaLoppuPvm = ZonedDateTime.now().plusDays(6)
                            )
                        )
                        .saveEntity(HankeStatus.PUBLIC)

                hankeCompletionService.sendReminderIfNecessary(hanke.id, HankeReminder.COMPLETION_5)

                val updatedHanke = hankeRepository.getReferenceById(hanke.id)
                assertThat(updatedHanke.sentReminders).isEmpty()
                assertThat(greenMail.receivedMessages).isEmpty()
            }

            @Test
            fun `does nothing if the reminder has been marked as sent`() {
                val hanke =
                    hankeFactory
                        .builder()
                        .withHankealue(
                            HankealueFactory.create(
                                haittaLoppuPvm = ZonedDateTime.now().plusDays(4)
                            )
                        )
                        .saveEntity(HankeStatus.PUBLIC) {
                            it.sentReminders = arrayOf(HankeReminder.COMPLETION_5)
                        }

                hankeCompletionService.sendReminderIfNecessary(hanke.id, HankeReminder.COMPLETION_5)

                val updatedHanke = hankeRepository.getReferenceById(hanke.id)
                assertThat(updatedHanke.sentReminders).containsExactly(HankeReminder.COMPLETION_5)
                assertThat(greenMail.receivedMessages).isEmpty()
            }

            @ParameterizedTest
            @ValueSource(ints = [1, 4, 5])
            fun `marks the reminder sent and sends the reminder when the hanke is ending soon`(
                date: Long
            ) {
                val endDate = ZonedDateTime.now().plusDays(date)
                val hanke =
                    hankeFactory
                        .builder()
                        .withHankealue(HankealueFactory.create(haittaLoppuPvm = endDate))
                        .saveEntity(HankeStatus.PUBLIC)

                hankeCompletionService.sendReminderIfNecessary(hanke.id, HankeReminder.COMPLETION_5)

                val updatedHanke = hankeRepository.getReferenceById(hanke.id)
                assertThat(updatedHanke.sentReminders).containsExactly(HankeReminder.COMPLETION_5)
                assertThat(greenMail.receivedMessages).hasSize(1)
                val email = greenMail.firstReceivedMessage()
                assertThat(email.allRecipients).hasSize(1)
                assertThat(email.allRecipients[0].toString()).isEqualTo("pertti@perustaja.test")
                assertThat(email.subject)
                    .isEqualTo(
                        "Haitaton: Hankkeesi ${hanke.hankeTunnus} päättymispäivä lähenee " +
                            "/ Hankkeesi ${hanke.hankeTunnus} päättymispäivä lähenee " +
                            "/ Hankkeesi ${hanke.hankeTunnus} päättymispäivä lähenee"
                    )
                val day = endDate.dayOfMonth
                val month = endDate.monthValue
                val year = endDate.year
                assertThat(email.textBody())
                    .contains(
                        "Hankkeesi ${hanke.nimi} (${hanke.hankeTunnus}) ilmoitettu päättymispäivä $day.$month.$year lähenee"
                    )
            }
        }

        @Nested
        inner class Completion14 {
            @Test
            fun `does nothing if the reminder is not yet due`() {
                val hanke =
                    hankeFactory
                        .builder()
                        .withHankealue(
                            HankealueFactory.create(
                                haittaLoppuPvm = ZonedDateTime.now().plusDays(15)
                            )
                        )
                        .saveEntity(HankeStatus.PUBLIC)

                hankeCompletionService.sendReminderIfNecessary(
                    hanke.id,
                    HankeReminder.COMPLETION_14,
                )

                val updatedHanke = hankeRepository.getReferenceById(hanke.id)
                assertThat(updatedHanke.sentReminders).isEmpty()
                assertThat(greenMail.receivedMessages).isEmpty()
            }

            @Test
            fun `marks the reminder sent but doesn't send anything when the hanke is due to be sent the next reminder`() {
                val hanke =
                    hankeFactory
                        .builder()
                        .withHankealue(
                            HankealueFactory.create(
                                haittaLoppuPvm = ZonedDateTime.now().plusDays(5)
                            )
                        )
                        .saveEntity(HankeStatus.PUBLIC)

                hankeCompletionService.sendReminderIfNecessary(
                    hanke.id,
                    HankeReminder.COMPLETION_14,
                )

                val updatedHanke = hankeRepository.getReferenceById(hanke.id)
                assertThat(updatedHanke.sentReminders).containsExactly(HankeReminder.COMPLETION_14)
                assertThat(greenMail.receivedMessages).isEmpty()
            }

            @Test
            fun `does nothing if the reminder has been marked as sent`() {
                val hanke =
                    hankeFactory
                        .builder()
                        .withHankealue(
                            HankealueFactory.create(
                                haittaLoppuPvm = ZonedDateTime.now().plusDays(13)
                            )
                        )
                        .saveEntity(HankeStatus.PUBLIC) {
                            it.sentReminders = arrayOf(HankeReminder.COMPLETION_14)
                        }

                hankeCompletionService.sendReminderIfNecessary(
                    hanke.id,
                    HankeReminder.COMPLETION_14,
                )

                val updatedHanke = hankeRepository.getReferenceById(hanke.id)
                assertThat(updatedHanke.sentReminders).containsExactly(HankeReminder.COMPLETION_14)
                assertThat(greenMail.receivedMessages).isEmpty()
            }

            @ParameterizedTest
            @ValueSource(ints = [6, 13, 14])
            fun `marks the 14-day reminder sent and sends the reminder when the hanke is ending soon`(
                date: Long
            ) {
                val endDate = ZonedDateTime.now().plusDays(date)
                val hanke =
                    hankeFactory
                        .builder()
                        .withHankealue(HankealueFactory.create(haittaLoppuPvm = endDate))
                        .saveEntity(HankeStatus.PUBLIC)

                hankeCompletionService.sendReminderIfNecessary(
                    hanke.id,
                    HankeReminder.COMPLETION_14,
                )

                val updatedHanke = hankeRepository.getReferenceById(hanke.id)
                assertThat(updatedHanke.sentReminders).containsExactly(HankeReminder.COMPLETION_14)
                assertThat(greenMail.receivedMessages).hasSize(1)
                val email = greenMail.firstReceivedMessage()
                assertThat(email.allRecipients).hasSize(1)
                assertThat(email.allRecipients[0].toString()).isEqualTo("pertti@perustaja.test")
                assertThat(email.subject)
                    .isEqualTo(
                        "Haitaton: Hankkeesi ${hanke.hankeTunnus} päättymispäivä lähenee " +
                            "/ Hankkeesi ${hanke.hankeTunnus} päättymispäivä lähenee " +
                            "/ Hankkeesi ${hanke.hankeTunnus} päättymispäivä lähenee"
                    )
                val day = endDate.dayOfMonth
                val month = endDate.monthValue
                val year = endDate.year
                assertThat(email.textBody())
                    .contains(
                        "Hankkeesi ${hanke.nimi} (${hanke.hankeTunnus}) ilmoitettu päättymispäivä $day.$month.$year lähenee"
                    )
            }
        }
    }

    @Nested
    inner class DeleteHanke {
        @ParameterizedTest
        @EnumSource(HankeStatus::class, names = ["COMPLETED"], mode = EnumSource.Mode.EXCLUDE)
        fun `throws exception when hanke is not completed`(status: HankeStatus) {
            val hanke = hankeFactory.builder().saveEntity(status)

            val failure = assertFailure { hankeCompletionService.deleteHanke(hanke.id) }

            failure.all {
                hasClass(HankeNotCompletedException::class)
                messageContains("Hanke is not completed")
                messageContains("id=${hanke.id}")
            }
        }

        @Test
        fun `throws exception when hanke has no completion date`() {
            val hanke =
                hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) { it.completedAt = null }

            val failure = assertFailure { hankeCompletionService.deleteHanke(hanke.id) }

            failure.all {
                hasClass(HankeHasNoCompletionDateException::class)
                messageContains("Hanke has no completion date")
                messageContains("id=${hanke.id}")
            }
        }

        @Test
        fun `throws exception when hanke completion date is too recently`() {
            val hanke =
                hankeFactory.builder().withNoAreas().saveEntity(HankeStatus.COMPLETED) {
                    it.completedAt = OffsetDateTime.now().minusMonths(6).plusDays(1)
                }

            val failure = assertFailure { hankeCompletionService.deleteHanke(hanke.id) }

            failure.all {
                hasClass(HankeCompletedRecently::class)
                messageContains("Hanke has been completed too recently")
                messageContains("id=${hanke.id}")
            }
        }

        @Test
        fun `does nothing when hanke has active applications`() {
            val hanke =
                hankeFactory.builder().withNoAreas().saveEntity(HankeStatus.COMPLETED) {
                    it.completedAt = OffsetDateTime.now().minusMonths(6)
                }
            hakemusFactory.builder(hanke).withStatus(ApplicationStatus.DECISIONMAKING).save()

            hankeCompletionService.deleteHanke(hanke.id)

            assertThat(hankeRepository.findOneById(hanke.id)).isNotNull().all {
                prop(HankeIdentifier::id).isEqualTo(hanke.id)
                prop(HankeIdentifier::hankeTunnus).isEqualTo(hanke.hankeTunnus)
            }
        }

        @Test
        fun `deletes the hanke with attachments and applications`() {
            val hanke =
                hankeFactory.builder().withNoAreas().saveEntity(HankeStatus.COMPLETED) {
                    it.completedAt = OffsetDateTime.now().minusMonths(6)
                }
            hakemusFactory
                .builder(hanke, ApplicationType.CABLE_REPORT)
                .withStatus(ApplicationStatus.FINISHED, alluId = 41)
                .save()
            hakemusFactory
                .builder(hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                .withStatus(ApplicationStatus.ARCHIVED, alluId = 53)
                .save()
            hankeAttachmentFactory.save(hanke = hanke).withContent()
            hankeAttachmentFactory.save(hanke = hanke).withContent()

            hankeCompletionService.deleteHanke(hanke.id)

            assertThat(hankeRepository.findAll()).isEmpty()
            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(Container.HANKE_LIITTEET)).isEmpty()
        }

        @Test
        fun `logs the deletion to audit logs`() {
            val hanke =
                hankeFactory.builder().withNoAreas().saveEntity(HankeStatus.COMPLETED) {
                    it.completedAt = OffsetDateTime.now().minusMonths(6)
                }
            auditLogRepository.deleteAll()

            hankeCompletionService.deleteHanke(hanke.id)

            assertThat(auditLogRepository.findAll()).single().isSuccess(Operation.DELETE) {
                hasServiceActor(HAITATON_AUDIT_LOG_USERID)
                withTarget {
                    hasId(hanke.id)
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.HANKE)
                    hasNoObjectAfter()
                }
            }
        }
    }

    companion object {
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
    }
}

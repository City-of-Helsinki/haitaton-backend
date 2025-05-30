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
import fi.hel.haitaton.hanke.HankeCompletionService.Companion.DAYS_BEFORE_COMPLETING_DRAFT
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.domain.HankeReminder
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.email.textBody
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeBuilder
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
import fi.hel.haitaton.hanke.test.TestUtils.FIXED_CLOCK
import java.time.LocalDate
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
    inner class IdsToComplete {

        @Test
        fun `with no hanke returns empty list`() {
            val result = hankeCompletionService.idsToComplete()

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns only public and draft hanke`() {
            val draftHanke = hankeFactory.builder().saveEntity(HankeStatus.DRAFT)
            hankeFactory.builder().saveEntity(HankeStatus.COMPLETED)
            val publicHanke = hankeFactory.builder().saveEntity(HankeStatus.PUBLIC)

            val result = hankeCompletionService.idsToComplete()

            assertThat(result).containsExactlyInAnyOrder(draftHanke.id, publicHanke.id)
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

            val result = hankeCompletionService.idsToComplete()

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

            val result = hankeCompletionService.idsToComplete()

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

            val result = hankeCompletionService.idsToComplete()

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
        fun `returns only public and draft hanke`() {
            val draftHanke = hankeFactory.builder().saveEntity(HankeStatus.DRAFT)
            hankeFactory.builder().saveEntity(HankeStatus.COMPLETED)
            val publicHanke = hankeFactory.builder().saveEntity(HankeStatus.PUBLIC)

            val result = hankeCompletionService.idsForReminders(HankeReminder.COMPLETION_14)

            assertThat(result).containsExactlyInAnyOrder(draftHanke.id, publicHanke.id)
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
                        HankealueFactory.create(
                            haittaLoppuPvm = ZonedDateTime.now(FIXED_CLOCK).plusDays(4)
                        )
                    )
                    .saveEntity(HankeStatus.PUBLIC)
            val onTheDayHanke =
                hankeFactory
                    .builder()
                    .withHankealue(
                        HankealueFactory.create(
                            haittaLoppuPvm = ZonedDateTime.now(FIXED_CLOCK).plusDays(5)
                        )
                    )
                    .saveEntity(HankeStatus.PUBLIC)
            hankeFactory
                .builder()
                .withHankealue(
                    HankealueFactory.create(
                        haittaLoppuPvm = ZonedDateTime.now(FIXED_CLOCK).plusDays(6)
                    )
                )
                .saveEntity(HankeStatus.PUBLIC)

            val result =
                hankeCompletionService.idsForReminders(HankeReminder.COMPLETION_5, FIXED_CLOCK)

            assertThat(result).containsExactly(justBeforeHanke.id, onTheDayHanke.id)
        }

        @Test
        fun `returns only hanke where the reminder hasn't been sent already`() {
            val unsentHanke =
                hankeFactory
                    .builder()
                    .withHankealue(
                        HankealueFactory.create(
                            haittaLoppuPvm = ZonedDateTime.now(FIXED_CLOCK).plusDays(5)
                        )
                    )
                    .saveEntity(HankeStatus.PUBLIC) { it.sentReminders = arrayOf() }
            hankeFactory
                .builder()
                .withHankealue(
                    HankealueFactory.create(
                        haittaLoppuPvm = ZonedDateTime.now(FIXED_CLOCK).plusDays(5)
                    )
                )
                .saveEntity(HankeStatus.PUBLIC) {
                    it.sentReminders = arrayOf(HankeReminder.COMPLETION_5)
                }

            val result =
                hankeCompletionService.idsForReminders(HankeReminder.COMPLETION_5, FIXED_CLOCK)

            assertThat(result).containsExactly(unsentHanke.id)
        }
    }

    @Nested
    inner class IdsToDelete {
        @Test
        fun `returns empty list if there are no hanke`() {
            val result = hankeCompletionService.idsToDelete()

            assertThat(result).isEmpty()
        }

        @Test
        fun `only returns hanke the completion date is at least 6 months in the past`() {
            val over6MonthsAgo =
                hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) {
                    it.completedAt =
                        OffsetDateTime.now(FIXED_CLOCK)
                            .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                            .minusDays(1)
                }
            val exactly6MonthsAgo =
                hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) {
                    it.completedAt =
                        OffsetDateTime.now(FIXED_CLOCK)
                            .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                }
            hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) {
                it.completedAt =
                    OffsetDateTime.now(FIXED_CLOCK)
                        .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                        .plusDays(1)
            }

            val result = hankeCompletionService.idsToDelete(FIXED_CLOCK)

            assertThat(result).containsExactly(over6MonthsAgo.id, exactly6MonthsAgo.id)
        }

        @Test
        fun `returns all matching hanke IDs ordered by completed at`() {
            val hankkeet =
                listOf(30, 2, 25, 15, 4).map { date ->
                    hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) {
                        it.completedAt =
                            OffsetDateTime.now(FIXED_CLOCK)
                                .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION + 1)
                                .withDayOfMonth(date)
                    }
                }

            val result = hankeCompletionService.idsToDelete(FIXED_CLOCK)

            // max-per-run is set to 3 in application-test.yml
            assertThat(result)
                .containsExactly(
                    hankkeet[1].id,
                    hankkeet[4].id,
                    hankkeet[3].id,
                    hankkeet[2].id,
                    hankkeet[0].id,
                )
        }
    }

    @Nested
    inner class IdsForDeletionReminders {
        @Test
        fun `returns empty list if there are no hanke`() {
            val result = hankeCompletionService.idsForDeletionReminders()

            assertThat(result).isEmpty()
        }

        @Test
        fun `only returns hanke the completion date is at least 6 months in the past`() {
            val justBeforeHanke =
                hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) {
                    it.completedAt =
                        OffsetDateTime.now(FIXED_CLOCK)
                            .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                            .plusDays(4)
                }
            val onTheDayHanke =
                hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) {
                    it.completedAt =
                        OffsetDateTime.now(FIXED_CLOCK)
                            .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                            .plusDays(5)
                }
            hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) {
                it.completedAt =
                    OffsetDateTime.now(FIXED_CLOCK)
                        .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                        .plusDays(6)
            }

            val result = hankeCompletionService.idsForDeletionReminders(FIXED_CLOCK)

            assertThat(result).containsExactly(justBeforeHanke.id, onTheDayHanke.id)
        }

        @Test
        fun `returns only hanke where the reminder hasn't been sent already`() {
            val unsentHanke =
                hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) {
                    it.completedAt =
                        OffsetDateTime.now(FIXED_CLOCK)
                            .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                            .plusDays(5)
                    it.sentReminders = arrayOf()
                }
            hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) {
                it.completedAt =
                    OffsetDateTime.now(FIXED_CLOCK)
                        .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                        .plusDays(5)
                it.sentReminders = arrayOf(HankeReminder.DELETION_5)
            }

            val result = hankeCompletionService.idsForDeletionReminders(FIXED_CLOCK)

            assertThat(result).containsExactly(unsentHanke.id)
        }

        @Test
        fun `returns all matching hanke IDs ordered by completed at`() {
            val hankkeet =
                listOf(30, 2, 25, 15, 4).map { date ->
                    hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) {
                        it.completedAt =
                            OffsetDateTime.now(FIXED_CLOCK)
                                .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION + 1)
                                .withDayOfMonth(date)
                    }
                }

            val result = hankeCompletionService.idsForDeletionReminders(FIXED_CLOCK)

            // max-per-run is set to 3 in application-test.yml
            assertThat(result)
                .containsExactly(
                    hankkeet[1].id,
                    hankkeet[4].id,
                    hankkeet[3].id,
                    hankkeet[2].id,
                    hankkeet[0].id,
                )
        }
    }

    @Nested
    inner class IdsForDraftsToComplete {
        private fun saveHanke(
            status: HankeStatus = HankeStatus.DRAFT,
            modifiedDaysAgo: Long = DAYS_BEFORE_COMPLETING_DRAFT,
            modifier: HankeBuilder.() -> HankeBuilder = { this },
        ) =
            hankeFactory.builder().modifier().saveEntity(status) {
                it.modifiedAt = getCurrentTimeUTCAsLocalTime().minusDays(modifiedDaysAgo)
            }

        @Test
        fun `returns empty list when there are no hanke`() {
            val result = hankeCompletionService.idsForDraftsToComplete()

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns only draft hanke`() {
            saveHanke(status = HankeStatus.COMPLETED)
            val draft = saveHanke()
            saveHanke(status = HankeStatus.PUBLIC)

            val result = hankeCompletionService.idsForDraftsToComplete()

            assertThat(result).containsExactly(draft.id)
        }

        @Test
        fun `returns only hanke with missing area end dates`() {
            val noAreas = saveHanke { withNoAreas() }
            saveHanke {
                withHankealue()
                withHankealue(haittaLoppuPvm = ZonedDateTime.now().minusMonths(7))
            }
            val missingEndDate = saveHanke {
                withHankealue()
                withHankealue(haittaLoppuPvm = null)
            }

            val result = hankeCompletionService.idsForDraftsToComplete()

            assertThat(result).containsExactlyInAnyOrder(noAreas.id, missingEndDate.id)
        }

        @Test
        fun `returns only hanke that haven't been modified in a long time`() {
            val onTheDay = saveHanke(modifiedDaysAgo = DAYS_BEFORE_COMPLETING_DRAFT)
            saveHanke(modifiedDaysAgo = DAYS_BEFORE_COMPLETING_DRAFT - 1)
            val beforeTheDay = saveHanke(modifiedDaysAgo = DAYS_BEFORE_COMPLETING_DRAFT + 1)

            val result = hankeCompletionService.idsForDraftsToComplete()

            assertThat(result).containsExactly(beforeTheDay.id, onTheDay.id)
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
        fun `throws exception when the hanke is completed`() {
            val hanke = baseHanke().saveEntity(HankeStatus.COMPLETED)

            val failure = assertFailure { hankeCompletionService.completeHankeIfPossible(hanke.id) }

            failure.hasClass(HankeCompletedException::class)
        }

        @Test
        fun `throws exception when a public hanke has no areas`() {
            val hanke = baseHanke().withNoAreas().saveEntity(HankeStatus.PUBLIC)

            val failure = assertFailure { hankeCompletionService.completeHankeIfPossible(hanke.id) }

            failure.hasClass(PublicHankeHasNoAreasException::class)
        }

        @Test
        fun `does nothing when a draft hanke has no areas`() {
            val hanke = hankeFactory.builder().withNoAreas().saveEntity(HankeStatus.DRAFT)

            hankeCompletionService.completeHankeIfPossible(hanke.id)

            val result = hankeRepository.getReferenceById(hanke.id)
            assertThat(result.status).isEqualTo(HankeStatus.DRAFT)
            assertThat(result.completedAt).isNull()
        }

        @Test
        fun `throws exception when a public hanke has an empty end date`() {
            val hanke =
                baseHanke()
                    .withHankealue(HankealueFactory.create(haittaLoppuPvm = null))
                    .saveEntity(HankeStatus.PUBLIC)

            val failure = assertFailure { hankeCompletionService.completeHankeIfPossible(hanke.id) }

            failure.hasClass(HankealueWithoutEndDateException::class)
        }

        @Test
        fun `does nothing when a draft hanke has an empty end date`() {
            val hanke =
                baseHanke()
                    .withHankealue(HankealueFactory.create(haittaLoppuPvm = null))
                    .saveEntity(HankeStatus.DRAFT)

            hankeCompletionService.completeHankeIfPossible(hanke.id)

            val result = hankeRepository.getReferenceById(hanke.id)
            assertThat(result.status).isEqualTo(HankeStatus.DRAFT)
            assertThat(result.completedAt).isNull()
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

        @ParameterizedTest
        @EnumSource(HankeStatus::class, names = ["DRAFT", "PUBLIC"])
        fun `changes hanke status when hanke has archived application`(status: HankeStatus) {
            val hanke = baseHanke().saveEntity(status)
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

        @Test
        fun `sends notification emails to hanke users with EDIT permissions`() {
            val hanke =
                baseHanke().saveWithYhteystiedot {
                    omistaja(Kayttooikeustaso.KAIKKI_OIKEUDET)
                    rakennuttaja(Kayttooikeustaso.KAIKKIEN_MUOKKAUS)
                    toteuttaja(Kayttooikeustaso.KATSELUOIKEUS)
                    muuYhteystieto(Kayttooikeustaso.HANKEMUOKKAUS)
                    toteuttaja(Kayttooikeustaso.HAKEMUSASIOINTI)
                }
            hanke.status = HankeStatus.PUBLIC
            hankeRepository.save(hanke)

            hankeCompletionService.completeHankeIfPossible(hanke.id)

            val emails = greenMail.receivedMessages
            val recipients = emails.map { it.allRecipients.single().toString() }
            assertThat(recipients).hasSize(4)
            assertThat(recipients)
                .containsExactlyInAnyOrder(
                    "pertti@perustaja.test",
                    "olivia.omistaja@mail.com",
                    "rane.rakennuttaja@mail.com",
                    "anssi.asianhoitaja@mail.com",
                )
            val email = emails.first()
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Hankkeesi ${hanke.hankeTunnus} ilmoitettu päättymispäivä on ohitettu " +
                        "/ Avslutningsdatum för ditt projekt ${hanke.hankeTunnus} har passerats " +
                        "/ The reported end date of your project ${hanke.hankeTunnus} has passed"
                )
            assertThat(email.textBody())
                .contains(
                    "Hankkeesi ${hanke.nimi} (${hanke.hankeTunnus}) ilmoitettu päättymispäivä on ohitettu. Hankkeesi ei enää näy muille Haitattoman käyttäjille, eikä sitä pääse enää muokkaamaan. Hanke säilyy Haitattomassa 6 kk, jona aikana pääset tarkastelemaan sitä."
                )
        }
    }

    @Nested
    inner class SendReminderIfNecessary {
        @Test
        fun `throws exception when the hanke is completed`() {
            val hanke = hankeFactory.builder().saveEntity(HankeStatus.COMPLETED)

            val failure = assertFailure {
                hankeCompletionService.sendReminderIfNecessary(hanke.id, HankeReminder.COMPLETION_5)
            }

            failure.hasClass(HankeCompletedException::class)
        }

        @Test
        fun `throws exception when a public hanke has no areas`() {
            val hanke = hankeFactory.builder().withNoAreas().saveEntity(HankeStatus.PUBLIC)

            val failure = assertFailure {
                hankeCompletionService.sendReminderIfNecessary(hanke.id, HankeReminder.COMPLETION_5)
            }

            failure.hasClass(PublicHankeHasNoAreasException::class)
        }

        @Test
        fun `does nothing when a draft hanke has no areas`() {
            val hanke = hankeFactory.builder().withNoAreas().saveEntity(HankeStatus.DRAFT)

            hankeCompletionService.sendReminderIfNecessary(hanke.id, HankeReminder.COMPLETION_5)

            assertThat(greenMail.receivedMessages).isEmpty()
            val updatedHanke = hankeRepository.getReferenceById(hanke.id)
            assertThat(updatedHanke.sentReminders).isEmpty()
        }

        @Test
        fun `throws exception when a public hanke has empty end dates`() {
            val hanke =
                hankeFactory
                    .builder()
                    .withHankealue(HankealueFactory.create(haittaLoppuPvm = null))
                    .withHankealue(
                        HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().plusDays(20))
                    )
                    .saveEntity(HankeStatus.PUBLIC)

            val failure = assertFailure {
                hankeCompletionService.sendReminderIfNecessary(
                    hanke.id,
                    HankeReminder.COMPLETION_14,
                )
            }

            failure.hasClass(HankealueWithoutEndDateException::class)
        }

        @Test
        fun `does nothing when a draft hanke has empty end dates`() {
            val hanke =
                hankeFactory
                    .builder()
                    .withHankealue(HankealueFactory.create(haittaLoppuPvm = null))
                    .withHankealue(
                        HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().plusDays(12))
                    )
                    .saveEntity(HankeStatus.DRAFT)

            hankeCompletionService.sendReminderIfNecessary(hanke.id, HankeReminder.COMPLETION_14)

            assertThat(greenMail.receivedMessages).isEmpty()
            val updatedHanke = hankeRepository.getReferenceById(hanke.id)
            assertThat(updatedHanke.sentReminders).isEmpty()
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

            @ParameterizedTest
            @EnumSource(HankeStatus::class, names = ["DRAFT", "PUBLIC"])
            fun `does nothing if the reminder has been marked as sent`(status: HankeStatus) {
                val hanke =
                    hankeFactory
                        .builder()
                        .withHankealue(
                            HankealueFactory.create(
                                haittaLoppuPvm = ZonedDateTime.now().plusDays(4)
                            )
                        )
                        .saveEntity(status) {
                            it.sentReminders = arrayOf(HankeReminder.COMPLETION_5)
                        }

                hankeCompletionService.sendReminderIfNecessary(hanke.id, HankeReminder.COMPLETION_5)

                val updatedHanke = hankeRepository.getReferenceById(hanke.id)
                assertThat(updatedHanke.sentReminders).containsExactly(HankeReminder.COMPLETION_5)
                assertThat(greenMail.receivedMessages).isEmpty()
            }

            @ParameterizedTest
            @ValueSource(ints = [0, 1])
            fun `marks the reminder sent but doesn't send anything when the hanke is due to be completed`(
                daysAgo: Long
            ) {
                val hanke =
                    hankeFactory
                        .builder()
                        .withHankealue(
                            HankealueFactory.create(
                                haittaLoppuPvm = ZonedDateTime.now().minusDays(daysAgo)
                            )
                        )
                        .saveEntity(HankeStatus.PUBLIC)

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
                val email = greenMail.firstReceivedMessage()
                assertThat(email.allRecipients).hasSize(1)
                assertThat(email.allRecipients[0].toString()).isEqualTo("pertti@perustaja.test")
                assertThat(email.subject)
                    .isEqualTo(
                        "Haitaton: Hankkeesi ${hanke.hankeTunnus} päättymispäivä lähenee " +
                            "/ Avslutningsdatum för ditt projekt ${hanke.hankeTunnus} närmar sig " +
                            "/ The end date of your project ${hanke.hankeTunnus} is approaching"
                    )
                val day = endDate.dayOfMonth
                val month = endDate.monthValue
                val year = endDate.year
                assertThat(email.textBody())
                    .contains(
                        "Hankkeesi ${hanke.nimi} (${hanke.hankeTunnus}) ilmoitettu päättymispäivä $day.$month.$year lähenee"
                    )
            }

            @Test
            fun `marks the reminder sent and sends the reminder also when the hanke is a draft`() {
                val endDate = ZonedDateTime.now().plusDays(5)
                val hanke =
                    hankeFactory
                        .builder()
                        .withHankealue(HankealueFactory.create(haittaLoppuPvm = endDate))
                        .saveEntity(HankeStatus.DRAFT)

                hankeCompletionService.sendReminderIfNecessary(hanke.id, HankeReminder.COMPLETION_5)

                val updatedHanke = hankeRepository.getReferenceById(hanke.id)
                assertThat(updatedHanke.sentReminders).containsExactly(HankeReminder.COMPLETION_5)
                val email = greenMail.firstReceivedMessage()
                assertThat(email.allRecipients).hasSize(1)
                assertThat(email.allRecipients[0].toString()).isEqualTo("pertti@perustaja.test")
                assertThat(email.subject)
                    .isEqualTo(
                        "Haitaton: Hankkeesi ${hanke.hankeTunnus} päättymispäivä lähenee " +
                            "/ Avslutningsdatum för ditt projekt ${hanke.hankeTunnus} närmar sig " +
                            "/ The end date of your project ${hanke.hankeTunnus} is approaching"
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

            @ParameterizedTest
            @EnumSource(HankeStatus::class, names = ["DRAFT", "PUBLIC"])
            fun `marks the reminder sent but doesn't send anything when the hanke is due to be sent the next reminder`(
                status: HankeStatus
            ) {
                val hanke =
                    hankeFactory
                        .builder()
                        .withHankealue(
                            HankealueFactory.create(
                                haittaLoppuPvm = ZonedDateTime.now().plusDays(5)
                            )
                        )
                        .saveEntity(status)

                hankeCompletionService.sendReminderIfNecessary(
                    hanke.id,
                    HankeReminder.COMPLETION_14,
                )

                val updatedHanke = hankeRepository.getReferenceById(hanke.id)
                assertThat(updatedHanke.sentReminders).containsExactly(HankeReminder.COMPLETION_14)
                assertThat(greenMail.receivedMessages).isEmpty()
            }

            @ParameterizedTest
            @ValueSource(ints = [0, 1])
            fun `marks the reminder sent but doesn't send anything when the hanke is due to be completed`(
                daysAgo: Long
            ) {
                val hanke =
                    hankeFactory
                        .builder()
                        .withHankealue(
                            HankealueFactory.create(
                                haittaLoppuPvm = ZonedDateTime.now().minusDays(daysAgo)
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

            @ParameterizedTest
            @EnumSource(HankeStatus::class, names = ["DRAFT", "PUBLIC"])
            fun `does nothing if the reminder has been marked as sent`(status: HankeStatus) {
                val hanke =
                    hankeFactory
                        .builder()
                        .withHankealue(
                            HankealueFactory.create(
                                haittaLoppuPvm = ZonedDateTime.now().plusDays(13)
                            )
                        )
                        .saveEntity(status) {
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
                val email = greenMail.firstReceivedMessage()
                assertThat(email.allRecipients).hasSize(1)
                assertThat(email.allRecipients[0].toString()).isEqualTo("pertti@perustaja.test")
                assertThat(email.subject)
                    .isEqualTo(
                        "Haitaton: Hankkeesi ${hanke.hankeTunnus} päättymispäivä lähenee " +
                            "/ Avslutningsdatum för ditt projekt ${hanke.hankeTunnus} närmar sig " +
                            "/ The end date of your project ${hanke.hankeTunnus} is approaching"
                    )
                val day = endDate.dayOfMonth
                val month = endDate.monthValue
                val year = endDate.year
                assertThat(email.textBody())
                    .contains(
                        "Hankkeesi ${hanke.nimi} (${hanke.hankeTunnus}) ilmoitettu päättymispäivä $day.$month.$year lähenee"
                    )
            }

            @Test
            fun `marks the 14-day reminder sent and sends the reminder also when the hanke is a draft`() {
                val endDate = ZonedDateTime.now().plusDays(14)
                val hanke =
                    hankeFactory
                        .builder()
                        .withHankealue(HankealueFactory.create(haittaLoppuPvm = endDate))
                        .saveEntity(HankeStatus.DRAFT)

                hankeCompletionService.sendReminderIfNecessary(
                    hanke.id,
                    HankeReminder.COMPLETION_14,
                )

                val updatedHanke = hankeRepository.getReferenceById(hanke.id)
                assertThat(updatedHanke.sentReminders).containsExactly(HankeReminder.COMPLETION_14)
                val email = greenMail.firstReceivedMessage()
                assertThat(email.allRecipients).hasSize(1)
                assertThat(email.allRecipients[0].toString()).isEqualTo("pertti@perustaja.test")
                assertThat(email.subject)
                    .isEqualTo(
                        "Haitaton: Hankkeesi ${hanke.hankeTunnus} päättymispäivä lähenee " +
                            "/ Avslutningsdatum för ditt projekt ${hanke.hankeTunnus} närmar sig " +
                            "/ The end date of your project ${hanke.hankeTunnus} is approaching"
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
                    it.completedAt =
                        OffsetDateTime.now()
                            .plusDays(1)
                            .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                }
            assertThat(hanke.deletionDate()).isEqualTo(LocalDate.now().plusDays(1))

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
                    it.completedAt =
                        OffsetDateTime.now()
                            .minusDays(1)
                            .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
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
                    it.completedAt =
                        OffsetDateTime.now()
                            .minusDays(1)
                            .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                }
            hakemusFactory
                .builder(hanke, ApplicationType.CABLE_REPORT)
                .withStatus(ApplicationStatus.FINISHED, alluId = 41)
                .save()
            hakemusFactory
                .builder(hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                .withNoAlluFields()
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
                    it.completedAt =
                        OffsetDateTime.now()
                            .minusDays(1)
                            .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
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

    @Nested
    inner class SendDeletionRemindersIfNecessary {
        @Test
        fun `throws exception when hanke is not COMPLETED`() {
            val hanke = hankeFactory.builder().saveEntity(HankeStatus.PUBLIC)

            val result = assertFailure {
                hankeCompletionService.sendDeletionRemindersIfNecessary(hanke.id)
            }

            result.all {
                hasClass(HankeNotCompletedException::class)
                messageContains("Hanke is not completed")
                messageContains("id=${hanke.id}")
            }
        }

        @Test
        fun `does nothing when notification has already been sent`() {
            val hanke =
                hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) {
                    it.completedAt =
                        OffsetDateTime.now().minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                    it.sentReminders = arrayOf(HankeReminder.DELETION_5)
                }

            hankeCompletionService.sendDeletionRemindersIfNecessary(hanke.id)

            assertThat(greenMail.receivedMessages).isEmpty()
        }

        @Test
        fun `throws exception when hanke has no completion date`() {
            val hanke =
                hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) { it.completedAt = null }

            val result = assertFailure {
                hankeCompletionService.sendDeletionRemindersIfNecessary(hanke.id)
            }

            result.all {
                hasClass(HankeHasNoCompletionDateException::class)
                messageContains("Hanke has no completion date")
                messageContains("id=${hanke.id}")
            }
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 1])
        fun `marks the reminder sent but doesn't send anything when the hanke is due to be deleted`(
            daysAgo: Long
        ) {
            val hanke =
                hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) {
                    it.completedAt =
                        OffsetDateTime.now(FIXED_CLOCK)
                            .minusDays(daysAgo)
                            .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                }
            assertThat(hanke.deletionDate())
                .isEqualTo(LocalDate.now(FIXED_CLOCK).minusDays(daysAgo))

            hankeCompletionService.sendDeletionRemindersIfNecessary(hanke.id)

            assertThat(greenMail.receivedMessages).isEmpty()
            val updatedHanke = hankeRepository.getReferenceById(hanke.id)
            assertThat(updatedHanke.sentReminders).containsExactly(HankeReminder.DELETION_5)
        }

        @Test
        fun `does nothing when the reminder is not yet due`() {
            val hanke =
                hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) {
                    it.completedAt =
                        OffsetDateTime.now(FIXED_CLOCK)
                            .plusDays(6)
                            .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                }
            assertThat(hanke.deletionDate()).isEqualTo(LocalDate.now(FIXED_CLOCK).plusDays(6))

            hankeCompletionService.sendDeletionRemindersIfNecessary(hanke.id, FIXED_CLOCK)

            assertThat(greenMail.receivedMessages).isEmpty()
            val updatedHanke = hankeRepository.getReferenceById(hanke.id)
            assertThat(updatedHanke.sentReminders).isEmpty()
        }

        @ParameterizedTest
        @ValueSource(ints = [1, 4, 5])
        fun `marks the reminder sent and sends the reminder when the hanke is ending soon`(
            date: Long
        ) {
            val hanke =
                hankeFactory.builder().saveEntity(HankeStatus.COMPLETED) {
                    it.completedAt =
                        OffsetDateTime.now(FIXED_CLOCK)
                            .plusDays(date)
                            .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                }
            assertThat(hanke.deletionDate()).isEqualTo(LocalDate.now(FIXED_CLOCK).plusDays(date))

            hankeCompletionService.sendDeletionRemindersIfNecessary(hanke.id, FIXED_CLOCK)

            val updatedHanke = hankeRepository.getReferenceById(hanke.id)
            assertThat(updatedHanke.sentReminders).containsExactly(HankeReminder.DELETION_5)
            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo("pertti@perustaja.test")
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Hankkeesi ${hanke.hankeTunnus} poistetaan järjestelmästä " +
                        "/ Ditt projekt ${hanke.hankeTunnus} raderas ur systemet " +
                        "/ Your project ${hanke.hankeTunnus} will be deleted from the system"
                )
            val deletionDate = OffsetDateTime.now(FIXED_CLOCK).plusDays(date)
            val day = deletionDate.dayOfMonth
            val month = deletionDate.monthValue
            val year = deletionDate.year
            assertThat(email.textBody())
                .contains(
                    "Hankkeesi ${hanke.nimi} (${hanke.hankeTunnus}) poistuu Haitattomasta $day.$month.$year."
                )
        }

        @Test
        fun `sends the reminder to everyone with EDIT permissions`() {
            val hanke =
                hankeFactory
                    .builder()
                    .withHankealue(
                        HankealueFactory.create(
                            haittaLoppuPvm =
                                OffsetDateTime.now(FIXED_CLOCK)
                                    .plusDays(5)
                                    .atZoneSameInstant(TZ_UTC)
                        )
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
                        muuYhteystieto(kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS)
                    }
            hanke.status = HankeStatus.COMPLETED
            hanke.completedAt =
                OffsetDateTime.now(FIXED_CLOCK)
                    .plusDays(3)
                    .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
            assertThat(hanke.deletionDate()).isEqualTo(LocalDate.now(FIXED_CLOCK).plusDays(3))
            hankeRepository.save(hanke)

            hankeCompletionService.sendDeletionRemindersIfNecessary(hanke.id, FIXED_CLOCK)

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
    }

    @Nested
    inner class CompleteDraftHankeIfPossible {
        @ParameterizedTest
        @EnumSource(HankeStatus::class, names = ["DRAFT"], mode = EnumSource.Mode.EXCLUDE)
        fun `throws exception when hanke is not draft`(status: HankeStatus) {
            val hanke = hankeFactory.builder().saveEntity(status)

            val failure = assertFailure {
                hankeCompletionService.completeDraftHankeIfPossible(hanke.id)
            }

            failure.all {
                hasClass(HankeNotDraftException::class)
                messageContains("Hanke is not a draft, it's $status")
                messageContains("id=${hanke.id}")
            }
        }

        @Test
        fun `does nothing when hanke has been modified recently`() {
            val hanke =
                hankeFactory.builder().saveEntity(HankeStatus.DRAFT) {
                    it.modifiedAt = getCurrentTimeUTCAsLocalTime().minusDays(179)
                }

            hankeCompletionService.completeDraftHankeIfPossible(hanke.id)

            assertThat(greenMail.receivedMessages).isEmpty()
            val result = hankeRepository.getReferenceById(hanke.id)
            assertThat(result.status).isEqualTo(HankeStatus.DRAFT)
            assertThat(result.completedAt).isNull()
        }

        @Test
        fun `does nothing when hanke has no modifiedAt value`() {
            val hanke =
                hankeFactory.builder().saveEntity(HankeStatus.DRAFT) { it.modifiedAt = null }

            hankeCompletionService.completeDraftHankeIfPossible(hanke.id)

            assertThat(greenMail.receivedMessages).isEmpty()
            val result = hankeRepository.getReferenceById(hanke.id)
            assertThat(result.status).isEqualTo(HankeStatus.DRAFT)
            assertThat(result.completedAt).isNull()
        }

        @Test
        fun `does nothing when hanke has end dates for all their areas`() {
            val hanke =
                hankeFactory.builder().withHankealue().saveEntity(HankeStatus.DRAFT) {
                    it.modifiedAt = getCurrentTimeUTCAsLocalTime().minusDays(180)
                }

            hankeCompletionService.completeDraftHankeIfPossible(hanke.id)

            assertThat(greenMail.receivedMessages).isEmpty()
            val result = hankeRepository.getReferenceById(hanke.id)
            assertThat(result.status).isEqualTo(HankeStatus.DRAFT)
            assertThat(result.completedAt).isNull()
        }

        @Test
        fun `does nothing when hanke has active applications`() {
            val hanke =
                hankeFactory.builder().withNoAreas().saveEntity(HankeStatus.DRAFT) {
                    it.modifiedAt = getCurrentTimeUTCAsLocalTime().minusDays(180)
                }
            hakemusFactory.builder(hanke).withStatus().saveEntity()

            hankeCompletionService.completeDraftHankeIfPossible(hanke.id)

            assertThat(greenMail.receivedMessages).isEmpty()
            val result = hankeRepository.getReferenceById(hanke.id)
            assertThat(result.status).isEqualTo(HankeStatus.DRAFT)
            assertThat(result.completedAt).isNull()
        }

        @Test
        fun `updates hanke status and completedAt when it has only finished applications`() {
            val hanke =
                hankeFactory.builder().withNoAreas().saveEntity(HankeStatus.DRAFT) {
                    it.modifiedAt = getCurrentTimeUTCAsLocalTime().minusDays(180)
                }
            hakemusFactory.builder(hanke).withStatus(ApplicationStatus.FINISHED).saveEntity()

            hankeCompletionService.completeDraftHankeIfPossible(hanke.id)

            val result = hankeRepository.getReferenceById(hanke.id)
            assertThat(result.status).isEqualTo(HankeStatus.COMPLETED)
            assertThat(result.completedAt).isNotNull().isRecent()
        }

        @Test
        fun `sends notification emails to hanke users with EDIT permissions`() {
            val hanke =
                hankeFactory.builder().withNoAreas().saveWithYhteystiedot {
                    omistaja(Kayttooikeustaso.KAIKKI_OIKEUDET)
                    rakennuttaja(Kayttooikeustaso.KAIKKIEN_MUOKKAUS)
                    toteuttaja(Kayttooikeustaso.KATSELUOIKEUS)
                    muuYhteystieto(Kayttooikeustaso.HANKEMUOKKAUS)
                    toteuttaja(Kayttooikeustaso.HAKEMUSASIOINTI)
                }
            hanke.status = HankeStatus.DRAFT
            hanke.modifiedAt = getCurrentTimeUTCAsLocalTime().minusDays(180)
            hankeRepository.save(hanke)

            hankeCompletionService.completeDraftHankeIfPossible(hanke.id)

            val emails = greenMail.receivedMessages
            val recipients = emails.map { it.allRecipients.single().toString() }
            assertThat(recipients).hasSize(4)
            assertThat(recipients)
                .containsExactlyInAnyOrder(
                    "pertti@perustaja.test",
                    "olivia.omistaja@mail.com",
                    "rane.rakennuttaja@mail.com",
                    "anssi.asianhoitaja@mail.com",
                )
            val email = emails.first()
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Hankkeesi ${hanke.hankeTunnus} ilmoitettu päättymispäivä on ohitettu " +
                        "/ Avslutningsdatum för ditt projekt ${hanke.hankeTunnus} har passerats " +
                        "/ The reported end date of your project ${hanke.hankeTunnus} has passed"
                )
            assertThat(email.textBody())
                .contains(
                    "Hankkeesi ${hanke.nimi} (${hanke.hankeTunnus}) ilmoitettu päättymispäivä on ohitettu. Hankkeesi ei enää näy muille Haitattoman käyttäjille, eikä sitä pääse enää muokkaamaan. Hanke säilyy Haitattomassa 6 kk, jona aikana pääset tarkastelemaan sitä."
                )
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

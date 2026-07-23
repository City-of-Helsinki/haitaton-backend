package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.HankeCompletionService.Companion.areasForDraft
import fi.hel.haitaton.hanke.HankeCompletionService.Companion.areasForPublic
import fi.hel.haitaton.hanke.HankeCompletionService.Companion.hasFutureAreas
import fi.hel.haitaton.hanke.HankeCompletionService.Companion.unmodifiedDraftReminderDays
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.domain.HankeReminder
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankealueFactory
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import java.time.LocalDate
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullSource

private typealias Service = HankeCompletionService

class HankeCompletionServiceTest {

    @Nested
    inner class AreasForPublic {
        @Test
        fun `throws exception when hanke has no areas`() {
            val hanke = HankeFactory.createMinimalEntity()

            val failure = assertFailure { hanke.areasForPublic() }

            failure.all {
                hasClass(PublicHankeHasNoAreasException::class.java)
                messageContains("Public hanke has no alueet")
                messageContains("id=${hanke.id}")
            }
        }

        @Test
        fun `returns areas when hanke has them`() {
            val hanke = HankeFactory.createMinimalEntity()
            hanke.alueet.add(HankealueFactory.createHankeAlueEntity(hankeEntity = hanke))

            val result = hanke.areasForPublic()

            assertThat(result).isSameInstanceAs(hanke.alueet)
        }
    }

    @Nested
    inner class AreasForDraft {
        @Test
        fun `returns null when hanke has no areas`() {
            val hanke = HankeFactory.createMinimalEntity()

            val result = hanke.areasForDraft()

            assertThat(result).isNull()
        }

        @Test
        fun `returns areas when hanke has them`() {
            val hanke = HankeFactory.createMinimalEntity()
            hanke.alueet.add(HankealueFactory.createHankeAlueEntity(hankeEntity = hanke))

            val result = hanke.areasForDraft()

            assertThat(result).isSameInstanceAs(hanke.alueet)
        }
    }

    @Nested
    inner class HasFutureAreas {
        @Test
        fun `return null when hanke has an area without an end date`() {
            val alueet = listOf(HankealueFactory.createHankeAlueEntity(haittaLoppuPvm = null))

            val result = alueet.hasFutureAreas()

            assertThat(result).isNull()
        }

        @Test
        fun `returns true when hanke has an area that ends after today`() {
            val alueet =
                listOf(
                    HankealueFactory.createHankeAlueEntity(
                        haittaLoppuPvm = LocalDate.now().plusDays(1)
                    )
                )

            val result = alueet.hasFutureAreas()

            assertThat(result).isNotNull().isTrue()
        }

        @Test
        fun `returns false when hanke has an area that ends in the past`() {
            val alueet =
                listOf(
                    HankealueFactory.createHankeAlueEntity(
                        haittaLoppuPvm = LocalDate.now().minusDays(1)
                    )
                )

            val result = alueet.hasFutureAreas()

            assertThat(result).isNotNull().isFalse()
        }

        @Test
        fun `returns false when hanke has an area that ends today`() {
            val alueet =
                listOf(HankealueFactory.createHankeAlueEntity(haittaLoppuPvm = LocalDate.now()))

            val result = alueet.hasFutureAreas()

            assertThat(result).isNotNull().isFalse()
        }

        @Test
        fun `returns true when hanke has both areas that ends before and after today`() {
            val alueet =
                listOf(
                    HankealueFactory.createHankeAlueEntity(
                        haittaLoppuPvm = LocalDate.now().plusDays(1)
                    ),
                    HankealueFactory.createHankeAlueEntity(
                        haittaLoppuPvm = LocalDate.now().minusDays(1)
                    ),
                )

            val result = alueet.hasFutureAreas()

            assertThat(result).isNotNull().isTrue()
        }
    }

    @Nested
    inner class HankeHasActiveApplications {

        @Test
        fun `returns false when hanke has no applications`() {
            val hanke = HankeFactory.createEntity()

            val result = Service.hankeHasActiveApplications(hanke)

            assertThat(result).isFalse()
        }

        @ParameterizedTest
        @NullSource
        @EnumSource(
            ApplicationStatus::class,
            names = ["FINISHED", "ARCHIVED", "DECISION"],
            mode = EnumSource.Mode.INCLUDE,
        )
        fun `returns false when hanke has a johtoselvityshakemus with null or completed status`(
            status: ApplicationStatus?
        ) {
            val hanke = HankeFactory.createEntity()
            hanke.hakemukset.add(
                ApplicationFactory.createApplicationEntity(
                    hanke = hanke,
                    alluStatus = status,
                    applicationType = ApplicationType.CABLE_REPORT,
                )
            )

            val result = Service.hankeHasActiveApplications(hanke)

            assertThat(result).isFalse()
        }

        @ParameterizedTest
        @EnumSource(
            ApplicationStatus::class,
            names = ["FINISHED", "ARCHIVED", "DECISION"],
            mode = EnumSource.Mode.EXCLUDE,
        )
        fun `returns true when hanke has a johtoselvityshakemus with an active status`(
            status: ApplicationStatus?
        ) {
            val hanke = HankeFactory.createEntity()
            hanke.hakemukset.add(
                ApplicationFactory.createApplicationEntity(
                    hanke = hanke,
                    alluStatus = status,
                    applicationType = ApplicationType.CABLE_REPORT,
                )
            )

            val result = Service.hankeHasActiveApplications(hanke)

            assertThat(result).isTrue()
        }

        @ParameterizedTest
        @NullSource
        @EnumSource(
            ApplicationStatus::class,
            names = ["FINISHED", "ARCHIVED"],
            mode = EnumSource.Mode.INCLUDE,
        )
        fun `returns false when hanke has a kaivuilmoitus with null or completed status`(
            status: ApplicationStatus?
        ) {
            val hanke = HankeFactory.createEntity()
            hanke.hakemukset.add(
                ApplicationFactory.createApplicationEntity(
                    hanke = hanke,
                    alluStatus = status,
                    applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                )
            )

            val result = Service.hankeHasActiveApplications(hanke)

            assertThat(result).isFalse()
        }

        @ParameterizedTest
        @EnumSource(
            ApplicationStatus::class,
            names = ["FINISHED", "ARCHIVED"],
            mode = EnumSource.Mode.EXCLUDE,
        )
        fun `returns true when hanke has a kaivuilmoitus with an active status`(
            status: ApplicationStatus?
        ) {
            val hanke = HankeFactory.createEntity()
            hanke.hakemukset.add(
                ApplicationFactory.createApplicationEntity(
                    hanke = hanke,
                    alluStatus = status,
                    applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                )
            )

            val result = Service.hankeHasActiveApplications(hanke)

            assertThat(result).isTrue()
        }

        @Test
        fun `returns true when hanke has both active and compeleted hakemus`() {
            val hanke = HankeFactory.createEntity()
            hanke.hakemukset.add(
                ApplicationFactory.createApplicationEntity(
                    hanke = hanke,
                    alluStatus = ApplicationStatus.ARCHIVED,
                    applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                )
            )
            hanke.hakemukset.add(
                ApplicationFactory.createApplicationEntity(
                    hanke = hanke,
                    alluStatus = ApplicationStatus.DECISIONMAKING,
                    applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                )
            )

            val result = Service.hankeHasActiveApplications(hanke)

            assertThat(result).isTrue()
        }
    }

    @Nested
    inner class UnmodifiedDraftReminderDays {
        @Test
        fun `returns correct days for DRAFT_COMPLETION_15 reminder`() {
            val result = unmodifiedDraftReminderDays(HankeReminder.DRAFT_COMPLETION_15)

            assertThat(result.daysUnmodified).isEqualTo(165)
            assertThat(result.daysUntilMarkedReady).isEqualTo(180)
        }

        @Test
        fun `returns correct days for DRAFT_COMPLETION_5 reminder`() {
            val result = unmodifiedDraftReminderDays(HankeReminder.DRAFT_COMPLETION_5)

            assertThat(result.daysUnmodified).isEqualTo(175)
            assertThat(result.daysUntilMarkedReady).isEqualTo(180)
        }

        @Test
        fun `throws exception for unsupported reminder type COMPLETION_5`() {
            val failure = assertFailure { unmodifiedDraftReminderDays(HankeReminder.COMPLETION_5) }

            failure.all {
                hasClass(IllegalArgumentException::class.java)
                messageContains("Unsupported reminder type for unmodified drafts: COMPLETION_5")
            }
        }

        @Test
        fun `throws exception for unsupported reminder type COMPLETION_14`() {
            val failure = assertFailure { unmodifiedDraftReminderDays(HankeReminder.COMPLETION_14) }

            failure.all {
                hasClass(IllegalArgumentException::class.java)
                messageContains("Unsupported reminder type for unmodified drafts: COMPLETION_14")
            }
        }

        @Test
        fun `throws exception for unsupported reminder type DELETION_5`() {
            val failure = assertFailure { unmodifiedDraftReminderDays(HankeReminder.DELETION_5) }

            failure.all {
                hasClass(IllegalArgumentException::class.java)
                messageContains("Unsupported reminder type for unmodified drafts: DELETION_5")
            }
        }
    }
}

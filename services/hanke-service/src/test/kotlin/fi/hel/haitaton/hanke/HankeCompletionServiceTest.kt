package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.domain.HankeStatus
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
    inner class AssertHankePublic {
        @ParameterizedTest
        @EnumSource(HankeStatus::class, names = ["PUBLIC"], mode = EnumSource.Mode.EXCLUDE)
        fun `throws exception when hanke is not public`(status: HankeStatus) {
            val hanke = HankeFactory.createEntity()
            hanke.status = status

            val failure = assertFailure { Service.assertHankePublic(hanke) }

            failure.all {
                hasClass(HankeNotPublicException::class.java)
                messageContains("Hanke is not public, it's $status")
                messageContains("id=${hanke.id}")
            }
        }

        @Test
        fun `passes when hanke is public`() {
            val hanke = HankeFactory.createEntity()
            hanke.status = HankeStatus.PUBLIC

            Service.assertHankePublic(hanke)
        }
    }

    @Nested
    inner class AssertHankeHasAreas {
        @Test
        fun `throws exception when hanke has no areas`() {
            val hanke = HankeFactory.createMinimalEntity()

            val failure = assertFailure { Service.assertHankeHasAreas(hanke) }

            failure.all {
                hasClass(PublicHankeHasNoAreasException::class.java)
                messageContains("Public hanke has no alueet")
                messageContains("id=${hanke.id}")
            }
        }

        @Test
        fun `passes when hanke has areas`() {
            val hanke = HankeFactory.createMinimalEntity()
            hanke.alueet.add(HankealueFactory.createHankeAlueEntity(hankeEntity = hanke))

            assertThat(Service.assertHankeHasAreas(hanke)).isEqualTo(Unit)
        }
    }

    @Nested
    inner class HankeHasFutureAreas {
        @Test
        fun `throws exception when hanke has an area without an end date`() {
            val hanke = HankeFactory.createMinimalEntity()
            hanke.alueet.add(
                HankealueFactory.createHankeAlueEntity(hankeEntity = hanke, haittaLoppuPvm = null)
            )

            val failure = assertFailure { Service.hankeHasFutureAreas(hanke) }

            failure.all {
                hasClass(HankealueWithoutEndDateException::class.java)
                messageContains("Public hanke has an alue without an end date")
                messageContains("id=${hanke.id}")
            }
        }

        @Test
        fun `returns true when hanke has an area that ends after today`() {
            val hanke = HankeFactory.createMinimalEntity()
            hanke.alueet.add(
                HankealueFactory.createHankeAlueEntity(
                    hankeEntity = hanke,
                    haittaLoppuPvm = LocalDate.now().plusDays(1),
                )
            )

            val result = Service.hankeHasFutureAreas(hanke)

            assertThat(result).isTrue()
        }

        @Test
        fun `returns false when hanke has an area that ends in the past`() {
            val hanke = HankeFactory.createMinimalEntity()
            hanke.alueet.add(
                HankealueFactory.createHankeAlueEntity(
                    hankeEntity = hanke,
                    haittaLoppuPvm = LocalDate.now().minusDays(1),
                )
            )

            val result = Service.hankeHasFutureAreas(hanke)

            assertThat(result).isFalse()
        }

        @Test
        fun `returns false when hanke has an area that ends today`() {
            val hanke = HankeFactory.createMinimalEntity()
            hanke.alueet.add(
                HankealueFactory.createHankeAlueEntity(
                    hankeEntity = hanke,
                    haittaLoppuPvm = LocalDate.now(),
                )
            )

            val result = Service.hankeHasFutureAreas(hanke)

            assertThat(result).isFalse()
        }

        @Test
        fun `returns true when hanke has both areas that ends before and after today`() {
            val hanke = HankeFactory.createMinimalEntity()
            hanke.alueet.add(
                HankealueFactory.createHankeAlueEntity(
                    hankeEntity = hanke,
                    haittaLoppuPvm = LocalDate.now().plusDays(1),
                )
            )
            hanke.alueet.add(
                HankealueFactory.createHankeAlueEntity(
                    hankeEntity = hanke,
                    haittaLoppuPvm = LocalDate.now().minusDays(1),
                )
            )

            val result = Service.hankeHasFutureAreas(hanke)

            assertThat(result).isTrue()
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
}

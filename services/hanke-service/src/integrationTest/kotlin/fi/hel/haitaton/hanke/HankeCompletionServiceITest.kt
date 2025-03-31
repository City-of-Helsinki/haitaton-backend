package fi.hel.haitaton.hanke

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasClass
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankealueFactory
import java.time.LocalDateTime
import java.time.ZonedDateTime
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class HankeCompletionServiceITest(
    @Autowired private val hankeCompletionService: HankeCompletionService,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val hankeRepository: HankeRepository,
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
            val publicHanke =
                hankeFactory
                    .builder()
                    .withHankealue(
                        HankealueFactory.create(haittaLoppuPvm = ZonedDateTime.now().minusDays(1))
                    )
                    .saveEntity(HankeStatus.PUBLIC)

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
            val hankkeet = (1..6).map { hankeFactory.builder().saveEntity(HankeStatus.PUBLIC) }
            val dates = listOf(30, 2, 25, null, 15, 4)
            val baseDate = LocalDateTime.parse("2025-03-01T09:54:05")
            dates.zip(hankkeet).forEach { (date, hanke) ->
                hanke.modifiedAt = date?.let { baseDate.withDayOfMonth(it) }
                hankeRepository.save(hanke)
            }

            val result = hankeCompletionService.getPublicIds()

            // max-per-run is set to 3 in application-test.yml
            assertThat(result)
                .containsExactlyInAnyOrder(hankkeet[1].id, hankkeet[5].id, hankkeet[4].id)
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
        }
    }
}

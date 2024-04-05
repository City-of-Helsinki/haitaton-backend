package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.factory.HankeFactory
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

class UtilsKtTest {

    @Nested
    inner class MergeDataInto {
        @Test
        fun `does nothing when both source and target are empty`() {
            val source = listOf<Hanke>()
            val target = mutableListOf<HankeEntity>()

            mergeDataInto(source, target) { _, b -> b!! }

            assertThat(target).isEmpty()
        }

        @Test
        fun `empties target when source is empty`() {
            val source = listOf<Hanke>()
            val target =
                mutableListOf(
                    HankeFactory.createMinimalEntity(id = 3),
                    HankeFactory.createMinimalEntity(id = 5)
                )

            mergeDataInto(source, target) { _, b -> b!! }

            assertThat(target).isEmpty()
        }

        @Test
        fun `converts source objects to target objects with the converter function`() {
            val source =
                listOf(
                    HankeFactory.create(id = 5),
                    HankeFactory.create(id = 7),
                    HankeFactory.create(id = 1),
                )
            val target = mutableListOf<HankeEntity>()

            mergeDataInto(source, target) { s, _ -> HankeFactory.createMinimalEntity(id = s.id) }

            assertThat(target).hasSize(3)
            assertThat(target[0].id).isEqualTo(5)
            assertThat(target[1].id).isEqualTo(7)
            assertThat(target[2].id).isEqualTo(1)
        }

        @Test
        fun `uses the target values in the converter where there are id matches`() {
            val source =
                listOf(
                    HankeFactory.create(id = 5, nimi = "New #5"),
                    HankeFactory.create(id = 7, nimi = "New #7"),
                    HankeFactory.create(id = 1, nimi = "New #1"),
                )
            val target =
                mutableListOf(
                    HankeFactory.createMinimalEntity(id = 1, nimi = "Original #1"),
                    HankeFactory.createMinimalEntity(id = 3, nimi = "Original #3"),
                    HankeFactory.createMinimalEntity(id = 5, nimi = "Original #5")
                )

            mergeDataInto(source, target) { s, t ->
                t ?: HankeFactory.createMinimalEntity(s.id, nimi = s.nimi)
            }

            assertThat(target).hasSize(3)
            assertThat(target[0].id).isEqualTo(5)
            assertThat(target[0].nimi).isEqualTo("Original #5")
            assertThat(target[1].id).isEqualTo(7)
            assertThat(target[1].nimi).isEqualTo("New #7")
            assertThat(target[2].id).isEqualTo(1)
            assertThat(target[2].nimi).isEqualTo("Original #1")
        }
    }

    @Nested
    inner class ValidBusinessId {
        @ParameterizedTest
        @ValueSource(
            strings =
                [
                    "2182805-0",
                    "7126070-7",
                    "1164243-9",
                    "3227510-5",
                    "3362438-9",
                    "7743551-2",
                    "8634465-5",
                    "0407327-4",
                    "7542843-1",
                    "6545312-3"
                ]
        )
        fun `isValid when valid businessId returns true`(businessId: String) {
            assertThat(businessId.isValidBusinessId()).isTrue()
        }

        @ParameterizedTest
        @ValueSource(
            strings =
                [
                    "21828053-0",
                    "71260-7",
                    "1164243-",
                    "3227510",
                    "3362438-4",
                    "0100007-1",
                    "823A445-7",
                    "8238445-A"
                ]
        )
        @NullSource
        fun `isValid when not valid businessId returns false`(businessId: String?) {
            assertThat(businessId.isValidBusinessId()).isFalse()
        }
    }

    @Nested
    inner class ValidOVT {

        @ParameterizedTest
        @ValueSource(
            strings =
                [
                    "003721828050",
                    "003721828050A",
                    "003721828050BB",
                    "003721828050CCC",
                    "003721828050D123",
                    "00372182805099999",
                ],
        )
        fun `returns true when is valid`(ovt: String) {
            assertThat(ovt.isValidOVT()).isTrue()
        }

        @ParameterizedTest
        @CsvSource("null", "11372182805", nullValues = ["null"])
        fun `returns false when is invalid`(ovt: String?) {
            assertThat(ovt.isValidOVT()).isFalse()
        }
    }
}

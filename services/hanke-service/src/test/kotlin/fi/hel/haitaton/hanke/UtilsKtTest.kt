package fi.hel.haitaton.hanke

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

class UtilsKtTest {

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
            assertTrue(businessId.isValidBusinessId())
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
            assertFalse(businessId.isValidBusinessId())
        }
    }
}

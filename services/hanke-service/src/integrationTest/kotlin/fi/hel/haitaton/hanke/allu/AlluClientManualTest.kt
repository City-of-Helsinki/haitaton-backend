package fi.hel.haitaton.hanke.allu

import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class AlluClientManualTest {

    @Test
    fun `load decision`() {
        val webCliebt =
            WebClient.builder()
                .codecs { codecs -> codecs.defaultCodecs().maxInMemorySize(100 * 1024 * 1024) }
                .build()
        val alluClient =
            AlluClient(
                webCliebt,
                AlluProperties(
                    System.getenv("ALLU_BASE_URL"),
                    System.getenv("ALLU_USERNAME"),
                    System.getenv("ALLU_PASSWORD"),
                    1,
                ),
            )

        val decisionPdf = alluClient.getDecisionPdf(129117)

        println("Decision PDF size: ${decisionPdf.size}")
    }
}

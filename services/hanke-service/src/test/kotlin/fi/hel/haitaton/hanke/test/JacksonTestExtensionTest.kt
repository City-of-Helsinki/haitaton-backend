package fi.hel.haitaton.hanke.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import fi.hel.haitaton.hanke.hakemus.HakemusResponse
import fi.hel.haitaton.hanke.hakemus.HankkeenHakemusResponse
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusDataResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tools.jackson.module.kotlin.readValue

/**
 * Regression coverage for the deserializers [JacksonTestExtension] registers on
 * [TEST_OBJECT_MAPPER] - see that class's doc comment for why they can't live on [OBJECT_MAPPER]
 * itself. A plain unit test rather than an integration test on purpose: this mechanism doesn't
 * need Docker/Postgres, only the extension running once to populate [TEST_OBJECT_MAPPER].
 *
 * Fixtures here use empty/null `areas` to avoid needing any GeoJSON geometry in the JSON - that's
 * an orthogonal concern (a pre-existing gap where [fi.hel.haitaton.hanke.OBJECT_MAPPER] itself
 * never got the GeoJSON `LngLatAlt` fix applied elsewhere in the app) and not what this test is
 * about.
 */
@ExtendWith(JacksonTestExtension::class)
class JacksonTestExtensionTest {
    @Test
    fun `TEST_OBJECT_MAPPER resolves HakemusResponse's polymorphic applicationData by applicationType`() {
        val json =
            """
            {
              "id": 1,
              "alluid": null,
              "alluStatus": null,
              "applicationIdentifier": null,
              "applicationType": "CABLE_REPORT",
              "hankeTunnus": "HAI-1",
              "valmistumisilmoitukset": null,
              "applicationData": {
                "applicationType": "CABLE_REPORT",
                "name": "test",
                "postalAddress": null,
                "constructionWork": false,
                "maintenanceWork": false,
                "propertyConnectivity": false,
                "emergencyWork": false,
                "rockExcavation": null,
                "workDescription": "desc",
                "startTime": null,
                "endTime": null,
                "areas": [],
                "paperDecisionReceiver": null,
                "customerWithContacts": null,
                "contractorWithContacts": null,
                "propertyDeveloperWithContacts": null,
                "representativeWithContacts": null
              }
            }
            """
                .trimIndent()

        val response: HakemusResponse = TEST_OBJECT_MAPPER.readValue(json)

        assertThat(response.applicationData).isInstanceOf(JohtoselvitysHakemusDataResponse::class)
        assertThat(response.applicationData.name).isEqualTo("test")
    }

    @Test
    fun `TEST_OBJECT_MAPPER resolves HankkeenHakemusResponse's abstract Hakemusalue mapping`() {
        val json =
            """
            {
              "id": 1,
              "alluid": null,
              "alluStatus": null,
              "applicationIdentifier": null,
              "applicationType": "CABLE_REPORT",
              "applicationData": {
                "name": "test",
                "startTime": null,
                "endTime": null,
                "areas": null
              },
              "muutosilmoitus": null,
              "paatokset": {}
            }
            """
                .trimIndent()

        val response: HankkeenHakemusResponse = TEST_OBJECT_MAPPER.readValue(json)

        assertThat(response.applicationData.name).isEqualTo("test")
    }
}

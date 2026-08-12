package fi.hel.haitaton.hanke.test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import fi.hel.haitaton.hanke.hakemus.HakemusResponse
import fi.hel.haitaton.hanke.hakemus.HankkeenHakemusResponse
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tools.jackson.module.kotlin.readValue

/**
 * Regression coverage for the deserializers [JacksonTestExtension] registers on
 * [TEST_OBJECT_MAPPER] - see that class's doc comment for why they can't live on [OBJECT_MAPPER]
 * itself. A plain unit test rather than an integration test on purpose: this mechanism doesn't
 * need Docker/Postgres, only the extension running once to populate [TEST_OBJECT_MAPPER].
 *
 * Fixtures here avoid any actual GeoJSON geometry (empty `tyoalueet`/`areas` lists rather than
 * omitting `areas` altogether) - that's an orthogonal concern (a pre-existing gap where
 * [fi.hel.haitaton.hanke.OBJECT_MAPPER] itself never got the GeoJSON `LngLatAlt` fix applied
 * elsewhere in the app) and not what this test is about. What matters is that `areas` is
 * non-empty and contains a real `Hakemusalue` element, so Jackson actually has to resolve the
 * sealed interface to a concrete class via `addAbstractTypeMapping` rather than skipping the
 * abstract-type resolution entirely.
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
              "applicationType": "EXCAVATION_NOTIFICATION",
              "applicationData": {
                "name": "test",
                "startTime": null,
                "endTime": null,
                "areas": [
                  {
                    "name": "area1",
                    "hankealueId": 1,
                    "tyoalueet": [],
                    "katuosoite": "Testikatu 1",
                    "tyonTarkoitukset": [],
                    "meluhaitta": "EI_MELUHAITTAA",
                    "polyhaitta": "EI_POLYHAITTAA",
                    "tarinahaitta": "EI_TARINAHAITTAA",
                    "kaistahaitta": "EI_VAIKUTA",
                    "kaistahaittojenPituus": "EI_VAIKUTA_KAISTAJARJESTELYIHIN",
                    "lisatiedot": null,
                    "haittojenhallintasuunnitelma": {}
                  }
                ]
              },
              "muutosilmoitus": null,
              "paatokset": {}
            }
            """
                .trimIndent()

        val response: HankkeenHakemusResponse = TEST_OBJECT_MAPPER.readValue(json)

        assertThat(response.applicationData.name).isEqualTo("test")
        val areas = response.applicationData.areas
        assertThat(areas!!).hasSize(1)
        assertThat(areas[0]).isInstanceOf(KaivuilmoitusAlue::class)
        assertThat((areas[0] as KaivuilmoitusAlue).katuosoite).isEqualTo("Testikatu 1")
    }
}

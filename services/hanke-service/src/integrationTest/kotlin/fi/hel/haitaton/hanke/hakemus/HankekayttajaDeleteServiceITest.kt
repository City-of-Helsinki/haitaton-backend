package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.extracting
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.permissions.HankeKayttajaNotFoundException
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.HankekayttajaDeleteService
import fi.hel.haitaton.hanke.permissions.HankekayttajaDeleteService.DeleteInfo
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.test.USERNAME
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired

class HankekayttajaDeleteServiceITest(
    @Autowired val deleteService: HankekayttajaDeleteService,
    @Autowired val hankeKayttajaService: HankeKayttajaService,
    @Autowired val hankeFactory: HankeFactory,
    @Autowired val hakemusFactory: HakemusFactory,
) : DatabaseTest() {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class CheckForDelete {
        @Test
        fun `throws exception when hankekayttaja does not exist`() {
            val kayttajaId = UUID.fromString("750b4207-2c5f-49a0-9b80-4829c807abeb")

            val failure = assertFailure { deleteService.checkForDelete(kayttajaId) }

            failure.all {
                hasClass(HankeKayttajaNotFoundException::class)
                messageContains(kayttajaId.toString())
            }
        }

        @Test
        fun `returns true for onlyOmistajanYhteyshenkilo when the user is only contact for omistaja`() {
            lateinit var perustaja: HankekayttajaEntity
            hankeFactory.builder().saveWithYhteystiedot {
                perustaja = hankeKayttajaService.getKayttajaByUserId(hankeEntity.id, USERNAME)!!
                omistaja(perustaja)
            }

            val response: DeleteInfo = deleteService.checkForDelete(perustaja.id)

            assertThat(response).all {
                prop(DeleteInfo::onlyOmistajanYhteyshenkilo).isTrue()
                prop(DeleteInfo::activeHakemukset).isEmpty()
                prop(DeleteInfo::draftHakemukset).isEmpty()
            }
        }

        @Test
        fun `returns true for onlyOmistajanYhteyshenkilo when the user is only contact for one omistaja`() {
            lateinit var perustaja: HankekayttajaEntity
            hankeFactory.builder().saveWithYhteystiedot {
                perustaja = hankeKayttajaService.getKayttajaByUserId(hankeEntity.id, USERNAME)!!
                omistaja(perustaja)
                omistaja(perustaja, kayttaja())
            }

            val response: DeleteInfo = deleteService.checkForDelete(perustaja.id)

            assertThat(response).all {
                prop(DeleteInfo::onlyOmistajanYhteyshenkilo).isTrue()
                prop(DeleteInfo::activeHakemukset).isEmpty()
                prop(DeleteInfo::draftHakemukset).isEmpty()
            }
        }

        @Test
        fun `returns false for onlyOmistajanYhteyshenkilo when there are no contacts`() {
            val hanke = hankeFactory.builder().save()
            val perustaja = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!

            val response: DeleteInfo = deleteService.checkForDelete(perustaja.id)

            assertThat(response).all {
                prop(DeleteInfo::onlyOmistajanYhteyshenkilo).isFalse()
                prop(DeleteInfo::activeHakemukset).isEmpty()
                prop(DeleteInfo::draftHakemukset).isEmpty()
            }
        }

        @Test
        fun `returns false for onlyOmistajanYhteyshenkilo when someone else is the contact`() {
            val hanke = hankeFactory.builder().saveWithYhteystiedot { omistaja() }
            val perustaja = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!

            val response: DeleteInfo = deleteService.checkForDelete(perustaja.id)

            assertThat(response).all {
                prop(DeleteInfo::onlyOmistajanYhteyshenkilo).isFalse()
                prop(DeleteInfo::activeHakemukset).isEmpty()
                prop(DeleteInfo::draftHakemukset).isEmpty()
            }
        }

        @Test
        fun `returns false for onlyOmistajanYhteyshenkilo when there is another contact as well`() {
            lateinit var perustaja: HankekayttajaEntity
            hankeFactory.builder().saveWithYhteystiedot {
                perustaja = hankeKayttajaService.getKayttajaByUserId(hankeEntity.id, USERNAME)!!
                omistaja()
            }

            val response: DeleteInfo = deleteService.checkForDelete(perustaja.id)

            assertThat(response).all {
                prop(DeleteInfo::onlyOmistajanYhteyshenkilo).isFalse()
                prop(DeleteInfo::activeHakemukset).isEmpty()
                prop(DeleteInfo::draftHakemukset).isEmpty()
            }
        }

        @Test
        fun `divides active and draft applications correctly`() {
            val hanke = hankeFactory.saveWithAlue()
            val perustaja = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            hakemusFactory.builder(hankeEntity = hanke).withName("Draft").saveWithYhteystiedot {
                hakija { addYhteyshenkilo(it, perustaja) }
            }
            hakemusFactory
                .builder(hankeEntity = hanke)
                .withName("Pending")
                .withStatus(ApplicationStatus.PENDING, alluId = 1, identifier = "JS230001")
                .saveWithYhteystiedot { hakija { addYhteyshenkilo(it, perustaja) } }
            hakemusFactory
                .builder(hankeEntity = hanke)
                .withName("Decision")
                .withStatus(ApplicationStatus.DECISION, alluId = 2, identifier = "JS230002")
                .saveWithYhteystiedot { hakija { addYhteyshenkilo(it, perustaja) } }

            val response: DeleteInfo = deleteService.checkForDelete(perustaja.id)

            assertThat(response).prop(DeleteInfo::draftHakemukset).single().all {
                prop(DeleteInfo.HakemusDetails::nimi).isEqualTo("Draft")
                prop(DeleteInfo.HakemusDetails::alluStatus).isNull()
                prop(DeleteInfo.HakemusDetails::applicationIdentifier).isNull()
            }
            assertThat(response).prop(DeleteInfo::activeHakemukset).all {
                hasSize(2)
                extracting { it.nimi }.containsExactlyInAnyOrder("Pending", "Decision")
                extracting { it.alluStatus }
                    .containsExactlyInAnyOrder(
                        ApplicationStatus.PENDING,
                        ApplicationStatus.DECISION,
                    )
                extracting { it.applicationIdentifier }
                    .containsExactlyInAnyOrder("JS230001", "JS230002")
            }
            assertThat(response).prop(DeleteInfo::onlyOmistajanYhteyshenkilo).isFalse()
        }
    }

    @Nested
    inner class GetHakemuksetForKayttaja {
        @Test
        fun `throws an exception when hankekayttaja does not exist`() {
            val kayttajaId = UUID.fromString("750b4207-2c5f-49a0-9b80-4829c807abeb")

            val failure = assertFailure { deleteService.getHakemuksetForKayttaja(kayttajaId) }

            failure.all {
                hasClass(HankeKayttajaNotFoundException::class)
                messageContains(kayttajaId.toString())
            }
        }

        @Test
        fun `returns an empty list when the kayttaja exists but there are no applications`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!

            val result = deleteService.getHakemuksetForKayttaja(founder.id)

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns applications when the kayttaja is an yhteyshenkilo`() {
            val hanke = hankeFactory.saveWithAlue(USERNAME)
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            val application1 =
                hakemusFactory.builder(USERNAME, hanke).saveWithYhteystiedot {
                    hakija { addYhteyshenkilo(it, founder) }
                    rakennuttaja()
                    tyonSuorittaja()
                }
            val application2 =
                hakemusFactory.builder(USERNAME, hanke).saveWithYhteystiedot {
                    hakija()
                    rakennuttaja()
                    tyonSuorittaja { addYhteyshenkilo(it, founder) }
                }

            val result = deleteService.getHakemuksetForKayttaja(founder.id)

            assertThat(result)
                .extracting { it.id }
                .containsExactlyInAnyOrder(application1.id, application2.id)
        }
    }
}

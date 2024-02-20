package fi.hel.haitaton.hanke.gdpr

import assertk.Assert
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.extracting
import assertk.assertions.first
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_HAKIJA
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.factory.PermissionFactory
import fi.hel.haitaton.hanke.permissions.HankeKayttajaDto
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionEntity
import fi.hel.haitaton.hanke.permissions.PermissionRepository
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

private const val USERID = "test-user"

@SpringBootTest(properties = ["haitaton.features.user-management=true"])
@ActiveProfiles("test")
@WithMockUser(USERID)
class KortistoGdprServiceITest(
    @Autowired val gdprService: GdprService,
    @Autowired val hankeKayttajaService: HankeKayttajaService,
    @Autowired val hankekayttajaRepository: HankekayttajaRepository,
    @Autowired val permissionRepository: PermissionRepository,
    @Autowired val hankeRepository: HankeRepository,
    @Autowired val applicationRepository: ApplicationRepository,
    @Autowired val hakemusFactory: HakemusFactory,
    @Autowired val hankeFactory: HankeFactory,
    @Autowired val hankekayttajaFactory: HankeKayttajaFactory,
) : DatabaseTest() {
    val OTHER_USER_ID = "Other user"

    @Test
    fun `Test class loads correct service`() {
        assertThat(gdprService).isInstanceOf(KortistoGdprService::class)
    }

    @Nested
    inner class FindGdprInfo {
        @Test
        fun `returns null when user has no permissions`() {
            val result = gdprService.findGdprInfo(USERID)

            assertThat(result).isNull()
        }

        @Test
        fun `returns null when user has permissions but no hankekayttaja`() {
            val hanke1 = hankeFactory.saveMinimal()
            val hanke2 = hankeFactory.saveMinimal()
            permissionRepository.save(
                PermissionFactory.createEntity(userId = USERID, hankeId = hanke1.id)
            )
            permissionRepository.save(
                PermissionFactory.createEntity(userId = USERID, hankeId = hanke2.id)
            )

            val result = gdprService.findGdprInfo(USERID)

            assertThat(result).isNull()
        }

        @Test
        fun `returns basic info when user has hankekayttaja`() {
            val hanke1 = hankeFactory.saveMinimal()
            val hanke2 = hankeFactory.saveMinimal()
            hankekayttajaFactory.saveIdentifiedUser(hanke1.id, userId = USERID)
            hankekayttajaFactory.saveIdentifiedUser(
                hanke2.id,
                etunimi = "Toinen",
                sukunimi = "Tohelo",
                sahkoposti = "toinen@tohelo.test",
                puhelin = "0009999999",
                userId = USERID
            )

            val result = gdprService.findGdprInfo(USERID)

            assertThat(result).isNotNull().all {
                prop(CollectionNode::children).hasSize(5)
                prop(CollectionNode::key).isEqualTo("user")
                hasStringChild("id", USERID)
                hasCollectionWithChildren(
                    "etunimet",
                    StringNode("etunimi", HankeKayttajaFactory.KAKE),
                    StringNode("etunimi", "Toinen")
                )
                hasCollectionWithChildren(
                    "sukunimet",
                    StringNode("sukunimi", HankeKayttajaFactory.KATSELIJA),
                    StringNode("sukunimi", "Tohelo")
                )
                hasCollectionWithChildren(
                    "puhelinnumerot",
                    StringNode("puhelinnumero", HankeKayttajaFactory.KAKE_PUHELIN),
                    StringNode("puhelinnumero", "0009999999")
                )
                hasCollectionWithChildren(
                    "sahkopostit",
                    StringNode("sahkoposti", HankeKayttajaFactory.KAKE_EMAIL),
                    StringNode("sahkoposti", "toinen@tohelo.test")
                )
            }
        }

        @Test
        fun `returns organization info when user is hankeyhteyshenkilo`() {
            val toteuttajaYhteystieto =
                HankeYhteystietoFactory.create(
                    id = null,
                    nimi = "Yritys Oy",
                    ytunnus = "4134328-8",
                    osasto = "Osasto"
                )
            val omistajaYhteystieto =
                HankeYhteystietoFactory.create(
                    id = null,
                    nimi = "Omistaja Oy",
                    ytunnus = "3213212-0",
                    osasto = null
                )
            hankeFactory.builder("other").saveWithYhteystiedot {
                val kayttaja = kayttaja(userId = USERID)
                omistaja(yhteystieto = omistajaYhteystieto) { addYhteyshenkilo(it, kayttaja) }
                toteuttaja(yhteystieto = toteuttajaYhteystieto) { addYhteyshenkilo(it, kayttaja) }
                rakennuttaja(yhteystieto = toteuttajaYhteystieto) { addYhteyshenkilo(it, kayttaja) }
                rakennuttaja(yhteystieto = toteuttajaYhteystieto) { addYhteyshenkilo(it, kayttaja) }
            }

            val result = gdprService.findGdprInfo(USERID)

            assertThat(result).isNotNull().all {
                prop(CollectionNode::key).isEqualTo("user")
                prop(CollectionNode::children).hasSize(6)
                hasStringChild("id", USERID)
                hasStringChild("etunimi", HankeKayttajaFactory.KAKE)
                hasStringChild("sukunimi", HankeKayttajaFactory.KATSELIJA)
                hasStringChild("sahkoposti", HankeKayttajaFactory.KAKE_EMAIL)
                hasStringChild("puhelinnumero", HankeKayttajaFactory.KAKE_PUHELIN)
                hasCollectionChild("organisaatiot") {
                    prop(CollectionNode::children).hasSize(2)
                    hasOrganisaatio(nimi = "Yritys Oy", ytunnus = "4134328-8", osasto = "Osasto")
                    hasOrganisaatio(nimi = "Omistaja Oy", ytunnus = "3213212-0")
                }
            }
        }

        @Test
        fun `returns organization info when user is hakemusyhteyshenkilo`() {
            val toteuttajaYhteystieto =
                HakemusyhteystietoFactory.create(
                    nimi = "Yritys Oy",
                    ytunnus = "4134328-8",
                )
            val omistajaYhteystieto =
                HakemusyhteystietoFactory.create(
                    nimi = "Omistaja Oy",
                    ytunnus = "3213212-0",
                )
            val hanke = hankeFactory.saveMinimal(generated = true)
            hakemusFactory.builder("other", hanke).saveWithYhteystiedot {
                val kayttaja = kayttaja(userId = USERID)
                hakija(yhteystieto = omistajaYhteystieto) { addYhteyshenkilo(it, kayttaja) }
                tyonSuorittaja(yhteystieto = toteuttajaYhteystieto) {
                    addYhteyshenkilo(it, kayttaja)
                }
                rakennuttaja(yhteystieto = toteuttajaYhteystieto) { addYhteyshenkilo(it, kayttaja) }
                asianhoitaja(yhteystieto = toteuttajaYhteystieto) { addYhteyshenkilo(it, kayttaja) }
            }

            val result = gdprService.findGdprInfo(USERID)

            assertThat(result).isNotNull().all {
                prop(CollectionNode::key).isEqualTo("user")
                prop(CollectionNode::children).hasSize(6)
                hasStringChild("id", USERID)
                hasStringChild("etunimi", HankeKayttajaFactory.KAKE)
                hasStringChild("sukunimi", HankeKayttajaFactory.KATSELIJA)
                hasStringChild("sahkoposti", HankeKayttajaFactory.KAKE_EMAIL)
                hasStringChild("puhelinnumero", HankeKayttajaFactory.KAKE_PUHELIN)
                hasCollectionChild("organisaatiot") {
                    prop(CollectionNode::children).hasSize(2)
                    hasOrganisaatio(nimi = "Yritys Oy", ytunnus = "4134328-8")
                    hasOrganisaatio(nimi = "Omistaja Oy", ytunnus = "3213212-0")
                }
            }
        }
    }

    @Nested
    inner class CanDelete {
        @Test
        fun `returns true when there's no data for the user`() {
            val result = gdprService.canDelete(USERID)

            assertThat(result).isTrue()
        }

        @Test
        fun `returns true when there's a hankekayttaja for the user, but no application`() {
            hankeFactory.builder(USERID).save()
            assertThat(permissionRepository.findAll())
                .first()
                .prop(PermissionEntity::userId)
                .isEqualTo(USERID)

            val result = gdprService.canDelete(USERID)

            assertThat(result).isTrue()
        }

        @Test
        fun `returns true when there are only draft applications`() {
            val hanke = hankeFactory.builder(USERID).withHankealue().saveEntity()
            val kayttaja = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERID)!!
            hakemusFactory.builder(USERID, hanke).saveWithYhteystiedot {
                asianhoitaja { addYhteyshenkilo(it, kayttaja) }
            }
            hakemusFactory.builder(USERID, hanke).saveWithYhteystiedot {
                rakennuttaja { addYhteyshenkilo(it, kayttaja) }
            }

            val result = gdprService.canDelete(USERID)

            assertThat(result).isTrue()
        }

        @Test
        fun `throws exception when there's an active application`() {
            val hanke = hankeFactory.builder(USERID).withHankealue().saveEntity()
            val kayttaja = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERID)!!
            hakemusFactory.builder(USERID, hanke).saveWithYhteystiedot {
                asianhoitaja { addYhteyshenkilo(it, kayttaja) }
            }
            val activeHakemus =
                hakemusFactory.builder(USERID, hanke).inHandling().saveWithYhteystiedot {
                    rakennuttaja { addYhteyshenkilo(it, kayttaja) }
                }

            val failure = assertFailure { gdprService.canDelete(USERID) }

            failure.given { e ->
                val messages = (e as DeleteForbiddenException).errors.map { it.message.fi }
                assertThat(messages).single().contains(activeHakemus.applicationIdentifier!!)
            }
        }

        @Test
        fun `returns true when the user is not an admin and not on any active applications`() {
            val adminUserId = "admin"
            val hanke = hankeFactory.builder(adminUserId).withHankealue().saveEntity()
            val adminKayttaja = hankeKayttajaService.getKayttajaByUserId(hanke.id, adminUserId)!!
            val targetKayttaja =
                hankekayttajaFactory.saveIdentifiedUser(
                    hanke.id,
                    userId = USERID,
                    kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS
                )
            hakemusFactory.builder(adminUserId, hanke).saveWithYhteystiedot {
                asianhoitaja { addYhteyshenkilo(it, targetKayttaja) }
            }
            hakemusFactory.builder(adminUserId, hanke).inHandling().saveWithYhteystiedot {
                rakennuttaja { addYhteyshenkilo(it, adminKayttaja) }
            }

            val result = gdprService.canDelete(USERID)

            assertThat(result).isTrue()
        }

        @Test
        fun `throws exception when user is the only admin and there's an active application on the hanke`() {
            val hanke = hankeFactory.builder(USERID).withHankealue().saveEntity()
            hakemusFactory.builder(USERID, hanke).saveWithYhteystiedot { asianhoitaja() }
            val activeHakemus =
                hakemusFactory.builder(USERID, hanke).inHandling().saveWithYhteystiedot {
                    rakennuttaja()
                }

            val failure = assertFailure { gdprService.canDelete(USERID) }

            failure.given { e ->
                val messages = (e as DeleteForbiddenException).errors.map { it.message.fi }
                assertThat(messages).single().contains(activeHakemus.applicationIdentifier!!)
            }
        }

        @Test
        fun `returns true when there's another admin even if there's an active application`() {
            val hanke = hankeFactory.builder(USERID).withHankealue().saveEntity()
            hakemusFactory.builder(USERID, hanke).saveWithYhteystiedot { asianhoitaja() }
            hakemusFactory.builder(USERID, hanke).inHandling().saveWithYhteystiedot {
                rakennuttaja(kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET)
            }

            val result = gdprService.canDelete(USERID)

            assertThat(result).isTrue()
        }
    }

    @Nested
    @ExtendWith(MockFileClientExtension::class)
    inner class DeleteInfo {
        @Test
        fun `doesn't throw an exception when there's no data for the user`() {
            gdprService.deleteInfo(USERID)
        }

        @Test
        fun `deletes hanke with applications when there are no other users`() {
            lateinit var kayttaja: HankekayttajaEntity
            val hanke =
                hankeFactory.builder(USERID).withHankealue().saveWithYhteystiedot {
                    kayttaja =
                        hankeKayttajaService.getKayttajaByUserId(this.hankeEntity.id, USERID)!!
                    omistaja { addYhteyshenkilo(it, kayttaja) }
                }
            hakemusFactory.builder(USERID, hanke).saveWithYhteystiedot {
                hakija { addYhteyshenkilo(it, kayttaja) }
            }

            gdprService.deleteInfo(USERID)

            assertThat(hankeRepository.findAll()).isEmpty()
            assertThat(applicationRepository.findAll()).isEmpty()
        }

        @Test
        fun `deletes hankekayttaja when there are other non-admin users`() {
            val hanke =
                hankeFactory.builder(USERID).withHankealue().saveWithYhteystiedot {
                    val kayttaja =
                        hankeKayttajaService.getKayttajaByUserId(this.hankeEntity.id, USERID)!!
                    omistaja { addYhteyshenkilo(it, kayttaja) }
                }
            val hakemus =
                hakemusFactory.builder(USERID, hanke).saveWithYhteystiedot {
                    hakija(kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI)
                }

            gdprService.deleteInfo(USERID)

            assertThat(hankeRepository.findAll()).single().prop(HankeEntity::id).isEqualTo(hanke.id)
            assertThat(applicationRepository.findAll())
                .single()
                .prop(ApplicationEntity::id)
                .isEqualTo(hakemus.id)
            assertThat(hankeKayttajaService.getKayttajatByHankeId(hanke.id)).single().all {
                prop(HankeKayttajaDto::kayttooikeustaso).isEqualTo(Kayttooikeustaso.HAKEMUSASIOINTI)
                prop(HankeKayttajaDto::sukunimi).isEqualTo(KAYTTAJA_INPUT_HAKIJA.sukunimi)
            }
        }

        @Test
        fun `throws an exception when the user is the only admin and there's an active application without the user`() {
            val hanke =
                hankeFactory.builder(USERID).withHankealue().saveWithYhteystiedot {
                    val kayttaja =
                        hankeKayttajaService.getKayttajaByUserId(this.hankeEntity.id, USERID)!!
                    omistaja { addYhteyshenkilo(it, kayttaja) }
                }
            val hakemus =
                hakemusFactory.builder(USERID, hanke).inHandling().saveWithYhteystiedot {
                    hakija(kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI)
                }

            val failure = assertFailure { gdprService.deleteInfo(USERID) }

            failure
                .isInstanceOf(DeleteForbiddenException::class)
                .prop(DeleteForbiddenException::errors)
                .single()
                .prop(GdprError::message)
                .prop(LocalizedMessage::fi)
                .contains(hakemus.applicationIdentifier!!)
        }

        @Test
        fun `deletes hankekayttaja when there's another admin in the hanke`() {
            lateinit var kayttaja: HankekayttajaEntity
            val hanke =
                hankeFactory.builder(USERID).withHankealue().saveWithYhteystiedot {
                    kayttaja =
                        hankeKayttajaService.getKayttajaByUserId(this.hankeEntity.id, USERID)!!
                    omistaja { addYhteyshenkilo(it, kayttaja) }
                }
            val hakemus =
                hakemusFactory.builder(USERID, hanke).inHandling().saveWithYhteystiedot {
                    hakija(
                        hakija = KAYTTAJA_INPUT_HAKIJA,
                        kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET,
                    )
                }

            gdprService.deleteInfo(USERID)

            assertThat(hankeRepository.findAll()).single().prop(HankeEntity::id).isEqualTo(hanke.id)
            assertThat(applicationRepository.findAll())
                .single()
                .prop(ApplicationEntity::id)
                .isEqualTo(hakemus.id)
            assertThat(hankeKayttajaService.getKayttajatByHankeId(hanke.id)).single().all {
                prop(HankeKayttajaDto::kayttooikeustaso).isEqualTo(Kayttooikeustaso.KAIKKI_OIKEUDET)
                prop(HankeKayttajaDto::sukunimi).isEqualTo(KAYTTAJA_INPUT_HAKIJA.sukunimi)
                prop(HankeKayttajaDto::id).isNotEqualTo(kayttaja.id)
            }
        }

        @Test
        fun `deletes hankekayttaja when the kayttaja is not an admin`() {
            lateinit var kayttaja: HankekayttajaEntity
            val hanke =
                hankeFactory.builder(OTHER_USER_ID).withHankealue().saveWithYhteystiedot {
                    kayttaja =
                        kayttaja(
                            kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI,
                            userId = USERID
                        )
                    omistaja { addYhteyshenkilo(it, kayttaja) }
                }
            val hakemus =
                hakemusFactory.builder(USERID, hanke).saveWithYhteystiedot {
                    hakija { addYhteyshenkilo(it, kayttaja) }
                }
            val founder: HankekayttajaEntity =
                hankeKayttajaService.getKayttajaByUserId(hanke.id, OTHER_USER_ID)!!

            gdprService.deleteInfo(USERID)

            assertThat(hankeRepository.findAll()).single().prop(HankeEntity::id).isEqualTo(hanke.id)
            assertThat(applicationRepository.findAll())
                .single()
                .prop(ApplicationEntity::id)
                .isEqualTo(hakemus.id)
            assertThat(hankeKayttajaService.getKayttajatByHankeId(hanke.id))
                .single()
                .prop(HankeKayttajaDto::id)
                .isEqualTo(founder.id)
        }

        @Test
        fun `throws an exception when the kayttaja is on an active application`() {
            val hanke =
                hankeFactory.builder(OTHER_USER_ID).withHankealue().saveWithYhteystiedot {
                    omistaja()
                }
            val kayttaja = hankekayttajaFactory.saveIdentifiedUser(hanke.id, userId = USERID)
            val hakemus =
                hakemusFactory.builder(USERID, hanke).inHandling().saveWithYhteystiedot {
                    hakija { addYhteyshenkilo(it, kayttaja) }
                }

            val failure = assertFailure { gdprService.deleteInfo(USERID) }

            failure
                .isInstanceOf(DeleteForbiddenException::class)
                .prop(DeleteForbiddenException::errors)
                .single()
                .prop(GdprError::message)
                .prop(LocalizedMessage::fi)
                .contains(hakemus.applicationIdentifier!!)
        }

        @Test
        fun `deletes the correct kayttajat and hankkeet when there are several`() {
            val unrelatedHanke = hankeFactory.builder(OTHER_USER_ID).withHankealue().saveEntity()
            val unrelatedHakemus =
                hakemusFactory.builder(OTHER_USER_ID, unrelatedHanke).saveWithYhteystiedot {
                    hakija()
                }
            lateinit var nonAdminKayttaja: HankekayttajaEntity
            val nonAdminHanke =
                hankeFactory.builder(OTHER_USER_ID).withHankealue().saveWithYhteystiedot {
                    nonAdminKayttaja = kayttaja(userId = USERID)
                    omistaja { addYhteyshenkilo(it, nonAdminKayttaja) }
                }
            val nonAdminHakemus =
                hakemusFactory.builder(USERID, nonAdminHanke).saveWithYhteystiedot {
                    hakija { addYhteyshenkilo(it, nonAdminKayttaja) }
                }
            lateinit var twoAdminKayttaja: HankekayttajaEntity
            val twoAdminHanke =
                hankeFactory.builder(USERID).withHankealue().saveWithYhteystiedot {
                    twoAdminKayttaja =
                        hankeKayttajaService.getKayttajaByUserId(this.hankeEntity.id, USERID)!!
                    omistaja { addYhteyshenkilo(it, twoAdminKayttaja) }
                }
            val twoAdminHakemus =
                hakemusFactory.builder(USERID, twoAdminHanke).inHandling().saveWithYhteystiedot {
                    hakija(kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET)
                }
            lateinit var soloKayttaja: HankekayttajaEntity
            val soloHanke =
                hankeFactory.builder(USERID).withHankealue().saveWithYhteystiedot {
                    soloKayttaja =
                        hankeKayttajaService.getKayttajaByUserId(this.hankeEntity.id, USERID)!!
                    omistaja { addYhteyshenkilo(it, soloKayttaja) }
                }
            hakemusFactory.builder(USERID, soloHanke).saveWithYhteystiedot {
                hakija { addYhteyshenkilo(it, soloKayttaja) }
            }
            val oneAdminHanke =
                hankeFactory.builder(USERID).withHankealue().saveWithYhteystiedot {
                    val kayttaja =
                        hankeKayttajaService.getKayttajaByUserId(this.hankeEntity.id, USERID)!!
                    omistaja { addYhteyshenkilo(it, kayttaja) }
                }
            val oneAdminHakemus =
                hakemusFactory.builder(USERID, oneAdminHanke).saveWithYhteystiedot {
                    hakija(kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI)
                }

            gdprService.deleteInfo(USERID)

            assertThat(hankeRepository.findAll())
                .extracting { it.id }
                .containsExactlyInAnyOrder(
                    unrelatedHanke.id,
                    nonAdminHanke.id,
                    twoAdminHanke.id,
                    oneAdminHanke.id,
                    // No soloHanke
                )
            assertThat(applicationRepository.findAll())
                .extracting { it.id }
                .containsExactlyInAnyOrder(
                    unrelatedHakemus.id,
                    nonAdminHakemus.id,
                    twoAdminHakemus.id,
                    oneAdminHakemus.id,
                )
            assertThat(hankekayttajaRepository.findByHankeId(unrelatedHanke.id))
                .extracting { it.permission!!.userId }
                .containsExactly(OTHER_USER_ID, HankeKayttajaFactory.FAKE_USERID)
            assertThat(hankekayttajaRepository.findByHankeId(nonAdminHanke.id))
                .extracting { it.permission!!.userId }
                .containsExactly(OTHER_USER_ID)
            assertThat(hankekayttajaRepository.findByHankeId(twoAdminHanke.id))
                .extracting { it.permission!!.userId }
                .containsExactly(HankeKayttajaFactory.FAKE_USERID)
            assertThat(hankekayttajaRepository.findByHankeId(oneAdminHanke.id))
                .extracting { it.permission!!.userId }
                .containsExactly(HankeKayttajaFactory.FAKE_USERID)
        }
    }
}

fun Assert<CollectionNode>.hasChild(key: String, body: Assert<Node>.() -> Unit) {
    prop(CollectionNode::children)
        .transform { it.filter { child -> child.key == key } }
        .single()
        .all(body)
}

fun Assert<CollectionNode>.hasNoChild(key: String) {
    prop(CollectionNode::children).transform { it.filter { child -> child.key == key } }.isEmpty()
}

fun Assert<CollectionNode>.hasStringChild(key: String, body: Assert<StringNode>.() -> Unit) {
    hasChild(key) { isInstanceOf(StringNode::class).all(body) }
}

fun Assert<CollectionNode>.hasStringChild(key: String, value: String) {
    hasStringChild(key) { prop(StringNode::value).isEqualTo(value) }
}

fun Assert<CollectionNode>.hasCollectionWithChildren(key: String, vararg child: Node) {
    prop(CollectionNode::children)
        .transform { it.filter { child -> child.key == key } }
        .single()
        .isInstanceOf(CollectionNode::class)
        .prop(CollectionNode::children)
        .containsExactlyInAnyOrder(*child)
}

fun Assert<CollectionNode>.hasCollectionChild(
    key: String,
    body: Assert<CollectionNode>.() -> Unit
) {
    hasChild(key) { isInstanceOf(CollectionNode::class).all(body) }
}

fun Assert<CollectionNode>.hasOrganisaatio(
    nimi: String,
    ytunnus: String? = null,
    osasto: String? = null
) {
    prop(CollectionNode::children)
        .transform { it.filter { child -> child.key == "organisaatio" } }
        .any {
            it.isInstanceOf(CollectionNode::class).all {
                hasStringChild("nimi", nimi)
                if (ytunnus == null) {
                    hasNoChild("tunnus")
                } else {
                    hasStringChild("tunnus", ytunnus)
                }
                if (osasto == null) {
                    hasNoChild("osasto")
                } else {
                    hasStringChild("osasto", osasto)
                }
            }
        }
}
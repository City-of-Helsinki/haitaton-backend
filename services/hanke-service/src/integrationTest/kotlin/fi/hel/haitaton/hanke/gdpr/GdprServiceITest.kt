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
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
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
import fi.hel.haitaton.hanke.test.USERNAME
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class GdprServiceITest(
    @Autowired val gdprService: GdprService,
    @Autowired val hankeKayttajaService: HankeKayttajaService,
    @Autowired val hankekayttajaRepository: HankekayttajaRepository,
    @Autowired val permissionRepository: PermissionRepository,
    @Autowired val hankeRepository: HankeRepository,
    @Autowired val applicationRepository: ApplicationRepository,
    @Autowired val hakemusFactory: HakemusFactory,
    @Autowired val hankeFactory: HankeFactory,
    @Autowired val hankekayttajaFactory: HankeKayttajaFactory,
) : IntegrationTest() {
    val OTHER_USER_ID = "Other user"

    @Test
    fun `Test class loads correct service`() {
        assertThat(gdprService).isInstanceOf(GdprService::class)
    }

    @Nested
    inner class FindGdprInfo {
        @Test
        fun `returns null when user has no permissions`() {
            val result = gdprService.findGdprInfo(USERNAME)

            assertThat(result).isNull()
        }

        @Test
        fun `returns null when user has permissions but no hankekayttaja`() {
            val hanke1 = hankeFactory.saveMinimal()
            val hanke2 = hankeFactory.saveMinimal()
            permissionRepository.save(
                PermissionFactory.createEntity(userId = USERNAME, hankeId = hanke1.id)
            )
            permissionRepository.save(
                PermissionFactory.createEntity(userId = USERNAME, hankeId = hanke2.id)
            )

            val result = gdprService.findGdprInfo(USERNAME)

            assertThat(result).isNull()
        }

        @Test
        fun `returns basic info when user has hankekayttaja`() {
            val hanke1 = hankeFactory.saveMinimal()
            val hanke2 = hankeFactory.saveMinimal()
            hankekayttajaFactory.saveIdentifiedUser(hanke1.id, userId = USERNAME)
            hankekayttajaFactory.saveIdentifiedUser(
                hanke2.id,
                etunimi = "Toinen",
                sukunimi = "Tohelo",
                sahkoposti = "toinen@tohelo.test",
                puhelin = "0009999999",
                userId = USERNAME
            )

            val result = gdprService.findGdprInfo(USERNAME)

            assertThat(result).isNotNull().all {
                prop(CollectionNode::children).hasSize(5)
                prop(CollectionNode::key).isEqualTo("user")
                hasStringChild("id", USERNAME)
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
                val kayttaja = kayttaja(userId = USERNAME)
                omistaja(yhteystieto = omistajaYhteystieto, kayttaja)
                toteuttaja(yhteystieto = toteuttajaYhteystieto, kayttaja)
                rakennuttaja(yhteystieto = toteuttajaYhteystieto, kayttaja)
                rakennuttaja(yhteystieto = toteuttajaYhteystieto, kayttaja)
            }

            val result = gdprService.findGdprInfo(USERNAME)

            assertThat(result).isNotNull().all {
                prop(CollectionNode::key).isEqualTo("user")
                prop(CollectionNode::children).hasSize(6)
                hasStringChild("id", USERNAME)
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
            val kayttaja = hankekayttajaFactory.saveIdentifiedUser(hanke.id, userId = USERNAME)
            hakemusFactory
                .builder("other", hanke)
                .hakija(yhteystieto = omistajaYhteystieto, kayttaja)
                .rakennuttaja(yhteystieto = toteuttajaYhteystieto, kayttaja)
                .asianhoitaja(yhteystieto = toteuttajaYhteystieto, kayttaja)
                .tyonSuorittaja(yhteystieto = toteuttajaYhteystieto, kayttaja)
                .save()

            val result = gdprService.findGdprInfo(USERNAME)

            assertThat(result).isNotNull().all {
                prop(CollectionNode::key).isEqualTo("user")
                prop(CollectionNode::children).hasSize(6)
                hasStringChild("id", USERNAME)
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
            val result = gdprService.canDelete(USERNAME)

            assertThat(result).isTrue()
        }

        @Test
        fun `returns true when there's a hankekayttaja for the user, but no application`() {
            hankeFactory.builder(USERNAME).save()
            assertThat(permissionRepository.findAll())
                .first()
                .prop(PermissionEntity::userId)
                .isEqualTo(USERNAME)

            val result = gdprService.canDelete(USERNAME)

            assertThat(result).isTrue()
        }

        @Test
        fun `returns true when there are only draft applications`() {
            val hanke = hankeFactory.saveWithAlue(USERNAME)
            val kayttaja = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            hakemusFactory.builder(hanke).asianhoitaja(kayttaja).save()
            hakemusFactory.builder(hanke).rakennuttaja(kayttaja).save()

            val result = gdprService.canDelete(USERNAME)

            assertThat(result).isTrue()
        }

        @Test
        fun `throws exception when there's an active application`() {
            val hanke = hankeFactory.saveWithAlue(USERNAME)
            val kayttaja = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            hakemusFactory.builder(hanke).asianhoitaja(kayttaja).save()
            val activeHakemus =
                hakemusFactory.builder(hanke).inHandling().rakennuttaja(kayttaja).save()

            val failure = assertFailure { gdprService.canDelete(USERNAME) }

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
                    userId = USERNAME,
                    kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS
                )
            hakemusFactory.builder(adminUserId, hanke).asianhoitaja(targetKayttaja).save()
            hakemusFactory
                .builder(adminUserId, hanke)
                .inHandling()
                .rakennuttaja(adminKayttaja)
                .save()

            val result = gdprService.canDelete(USERNAME)

            assertThat(result).isTrue()
        }

        @Test
        fun `throws exception when user is the only admin and there's an active application on the hanke`() {
            val hanke = hankeFactory.saveWithAlue(USERNAME)
            hakemusFactory.builder(hanke).asianhoitaja().save()
            val activeHakemus = hakemusFactory.builder(hanke).inHandling().rakennuttaja().save()

            val failure = assertFailure { gdprService.canDelete(USERNAME) }

            failure.given { e ->
                val messages = (e as DeleteForbiddenException).errors.map { it.message.fi }
                assertThat(messages).single().contains(activeHakemus.applicationIdentifier!!)
            }
        }

        @Test
        fun `returns true when there's another admin even if there's an active application`() {
            val hanke = hankeFactory.saveWithAlue(USERNAME)
            hakemusFactory.builder(hanke).asianhoitaja().save()
            hakemusFactory
                .builder(hanke)
                .inHandling()
                .rakennuttaja(Kayttooikeustaso.KAIKKI_OIKEUDET)
                .save()

            val result = gdprService.canDelete(USERNAME)

            assertThat(result).isTrue()
        }
    }

    @Nested
    inner class DeleteInfo {
        @Test
        fun `doesn't throw an exception when there's no data for the user`() {
            gdprService.deleteInfo(USERNAME)
        }

        @Test
        fun `deletes hanke with applications when there are no other users`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            hankeFactory.addYhteystiedotTo(hanke) { omistaja(founder) }
            hakemusFactory.builder(hanke).hakija(founder).save()

            gdprService.deleteInfo(USERNAME)

            assertThat(hankeRepository.findAll()).isEmpty()
            assertThat(applicationRepository.findAll()).isEmpty()
        }

        @Test
        fun `deletes hankekayttaja when there are other non-admin users`() {
            val hanke =
                hankeFactory.builder(USERNAME).withHankealue().saveWithYhteystiedot {
                    val kayttaja =
                        hankeKayttajaService.getKayttajaByUserId(hankeEntity.id, USERNAME)!!
                    omistaja(kayttaja)
                }
            val hakemus =
                hakemusFactory.builder(hanke).hakija(Kayttooikeustaso.HAKEMUSASIOINTI).save()

            gdprService.deleteInfo(USERNAME)

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
                hankeFactory.builder(USERNAME).withHankealue().saveWithYhteystiedot {
                    val kayttaja =
                        hankeKayttajaService.getKayttajaByUserId(hankeEntity.id, USERNAME)!!
                    omistaja(kayttaja)
                }
            val hakemus =
                hakemusFactory
                    .builder(hanke)
                    .inHandling()
                    .hakija(Kayttooikeustaso.HAKEMUSASIOINTI)
                    .save()

            val failure = assertFailure { gdprService.deleteInfo(USERNAME) }

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
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            hankeFactory.addYhteystiedotTo(hanke) { omistaja(founder) }
            val hakemus =
                hakemusFactory
                    .builder(hanke)
                    .inHandling()
                    .hakija(Kayttooikeustaso.KAIKKI_OIKEUDET)
                    .save()

            gdprService.deleteInfo(USERNAME)

            assertThat(hankeRepository.findAll()).single().prop(HankeEntity::id).isEqualTo(hanke.id)
            assertThat(applicationRepository.findAll())
                .single()
                .prop(ApplicationEntity::id)
                .isEqualTo(hakemus.id)
            assertThat(hankeKayttajaService.getKayttajatByHankeId(hanke.id)).single().all {
                prop(HankeKayttajaDto::kayttooikeustaso).isEqualTo(Kayttooikeustaso.KAIKKI_OIKEUDET)
                prop(HankeKayttajaDto::sukunimi).isEqualTo(KAYTTAJA_INPUT_HAKIJA.sukunimi)
                prop(HankeKayttajaDto::id).isNotEqualTo(founder.id)
            }
        }

        @Test
        fun `deletes hankekayttaja when the kayttaja is not an admin`() {
            val hanke = hankeFactory.builder(OTHER_USER_ID).withHankealue().saveEntity()
            val kayttaja =
                hankekayttajaFactory.saveIdentifiedUser(
                    kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI,
                    userId = USERNAME,
                    hankeId = hanke.id,
                )
            hankeFactory.addYhteystiedotTo(hanke) { omistaja(kayttaja) }
            val hakemus = hakemusFactory.builder(hanke).hakija(kayttaja).save()
            val founder: HankekayttajaEntity =
                hankeKayttajaService.getKayttajaByUserId(hanke.id, OTHER_USER_ID)!!

            gdprService.deleteInfo(USERNAME)

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
            val kayttaja = hankekayttajaFactory.saveIdentifiedUser(hanke.id, userId = USERNAME)
            val hakemus = hakemusFactory.builder(hanke).inHandling().hakija(kayttaja).save()

            val failure = assertFailure { gdprService.deleteInfo(USERNAME) }

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
                hakemusFactory.builder(OTHER_USER_ID, unrelatedHanke).hakija().save()
            lateinit var nonAdminKayttaja: HankekayttajaEntity
            val nonAdminHanke =
                hankeFactory.builder(OTHER_USER_ID).withHankealue().saveWithYhteystiedot {
                    nonAdminKayttaja = kayttaja(userId = USERNAME)
                    omistaja(nonAdminKayttaja)
                }
            val nonAdminHakemus =
                hakemusFactory.builder(nonAdminHanke).hakija(nonAdminKayttaja).save()
            lateinit var twoAdminKayttaja: HankekayttajaEntity
            val twoAdminHanke =
                hankeFactory.builder(USERNAME).withHankealue().saveWithYhteystiedot {
                    twoAdminKayttaja =
                        hankeKayttajaService.getKayttajaByUserId(hankeEntity.id, USERNAME)!!
                    omistaja(twoAdminKayttaja)
                }
            val twoAdminHakemus =
                hakemusFactory
                    .builder(twoAdminHanke)
                    .inHandling()
                    .hakija(Kayttooikeustaso.KAIKKI_OIKEUDET)
                    .save()
            lateinit var soloKayttaja: HankekayttajaEntity
            val soloHanke =
                hankeFactory.builder(USERNAME).withHankealue().saveWithYhteystiedot {
                    soloKayttaja =
                        hankeKayttajaService.getKayttajaByUserId(hankeEntity.id, USERNAME)!!
                    omistaja(soloKayttaja)
                }
            hakemusFactory.builder(soloHanke).hakija(soloKayttaja).save()
            val oneAdminHanke =
                hankeFactory.builder(USERNAME).withHankealue().saveWithYhteystiedot {
                    val kayttaja =
                        hankeKayttajaService.getKayttajaByUserId(hankeEntity.id, USERNAME)!!
                    omistaja(kayttaja)
                }
            val oneAdminHakemus =
                hakemusFactory
                    .builder(oneAdminHanke)
                    .hakija(Kayttooikeustaso.HAKEMUSASIOINTI)
                    .save()

            gdprService.deleteInfo(USERNAME)

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

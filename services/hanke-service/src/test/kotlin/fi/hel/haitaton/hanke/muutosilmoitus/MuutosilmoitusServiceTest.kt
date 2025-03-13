package fi.hel.haitaton.hanke.muutosilmoitus

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteyshenkiloFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MuutosilmoitusServiceTest {

    @Nested
    inner class MergeMuutosilmoitusToHakemus {

        @Test
        fun `updates hakemus data from the muutosilmoitus`() {
            val baseData = ApplicationFactory.createCableReportApplicationData()
            val hakemusData = baseData.copy(areas = listOf())
            val hakemus = HakemusFactory.createEntity(hakemusEntityData = hakemusData)
            val muutosilmoitusData =
                baseData.copy(
                    startTime = baseData.startTime!!.minusDays(1),
                    endTime = baseData.endTime!!.plusDays(1),
                )
            val muutosilmoitus =
                MuutosilmoitusFactory.createEntity(hakemusData = muutosilmoitusData)

            MuutosilmoitusService.mergeMuutosilmoitusToHakemus(muutosilmoitus, hakemus)

            assertThat(hakemus.hakemusEntityData).isEqualTo(muutosilmoitusData)
        }

        @Test
        fun `removes yhteystieto from hakemus when not present in muutosilmoitus`() {
            val hakemus = HakemusFactory.createEntity()
            hakemus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA] =
                HakemusyhteystietoFactory.createEntity(application = hakemus)
            val muutosilmoitus = MuutosilmoitusFactory.createEntity()
            muutosilmoitus.yhteystiedot.remove(ApplicationContactType.RAKENNUTTAJA)

            MuutosilmoitusService.mergeMuutosilmoitusToHakemus(muutosilmoitus, hakemus)

            assertThat(hakemus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA]).isNull()
        }

        @Test
        fun `creates yhteystieto to hakemus when new one added in muutosilmoitus`() {
            val hakemus = HakemusFactory.createEntity()
            hakemus.yhteystiedot.remove(ApplicationContactType.RAKENNUTTAJA)
            val muutosilmoitus = MuutosilmoitusFactory.createEntity()
            val yhteystieto =
                MuutosilmoitusFactory.createYhteystietoEntity(
                    muutosilmoitus = muutosilmoitus,
                    rooli = ApplicationContactType.RAKENNUTTAJA,
                )
            val yhteyshenkilo =
                MuutosilmoitusFactory.createYhteyshenkiloEntity(hakemus.hanke.id, yhteystieto)
            yhteystieto.yhteyshenkilot.add(yhteyshenkilo)
            muutosilmoitus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA] = yhteystieto

            MuutosilmoitusService.mergeMuutosilmoitusToHakemus(muutosilmoitus, hakemus)

            assertThat(hakemus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA]).isNotNull().all {
                prop(HakemusyhteystietoEntity::id).isNotEqualTo(yhteystieto.id)
                prop(HakemusyhteystietoEntity::application).isEqualTo(hakemus)
                prop(HakemusyhteystietoEntity::tyyppi).isEqualTo(yhteystieto.tyyppi)
                prop(HakemusyhteystietoEntity::rooli).isEqualTo(ApplicationContactType.RAKENNUTTAJA)
                prop(HakemusyhteystietoEntity::nimi).isEqualTo(yhteystieto.nimi)
                prop(HakemusyhteystietoEntity::sahkoposti).isEqualTo(yhteystieto.sahkoposti)
                prop(HakemusyhteystietoEntity::puhelinnumero).isEqualTo(yhteystieto.puhelinnumero)
                prop(HakemusyhteystietoEntity::registryKey).isEqualTo(yhteystieto.registryKey)
                prop(HakemusyhteystietoEntity::yhteyshenkilot).single().all {
                    prop(HakemusyhteyshenkiloEntity::id).isNotEqualTo(yhteyshenkilo.id)
                    prop(HakemusyhteyshenkiloEntity::hankekayttaja)
                        .isEqualTo(yhteyshenkilo.hankekayttaja)
                    prop(HakemusyhteyshenkiloEntity::tilaaja).isEqualTo(yhteyshenkilo.tilaaja)
                }
            }
        }

        @Test
        fun `updates the existing yhteystieto in hakemus when updated in muutosilmoitus`() {
            val hakemus = HakemusFactory.createEntity()
            val hankeId = hakemus.hanke.id
            val hakemusyhteystieto =
                HakemusyhteystietoFactory.createEntity(
                    application = hakemus,
                    rooli = ApplicationContactType.RAKENNUTTAJA,
                )
            hakemus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA] = hakemusyhteystieto

            val muutosilmoitus = MuutosilmoitusFactory.createEntity()
            val yhteystieto =
                MuutosilmoitusFactory.createYhteystietoEntity(
                    muutosilmoitus = muutosilmoitus,
                    rooli = ApplicationContactType.RAKENNUTTAJA,
                    nimi = "New name",
                    sahkoposti = "new@email",
                )
            val yhteyshenkilo =
                MuutosilmoitusFactory.createYhteyshenkiloEntity(hankeId, yhteystieto)
            yhteystieto.yhteyshenkilot.add(yhteyshenkilo)
            muutosilmoitus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA] = yhteystieto

            MuutosilmoitusService.mergeMuutosilmoitusToHakemus(muutosilmoitus, hakemus)

            assertThat(hakemus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA]).isNotNull().all {
                prop(HakemusyhteystietoEntity::id).isNotEqualTo(yhteystieto.id)
                prop(HakemusyhteystietoEntity::id).isEqualTo(hakemusyhteystieto.id)
                prop(HakemusyhteystietoEntity::application).isEqualTo(hakemus)
                prop(HakemusyhteystietoEntity::tyyppi).isEqualTo(yhteystieto.tyyppi)
                prop(HakemusyhteystietoEntity::rooli).isEqualTo(ApplicationContactType.RAKENNUTTAJA)
                prop(HakemusyhteystietoEntity::nimi).isEqualTo(yhteystieto.nimi)
                prop(HakemusyhteystietoEntity::sahkoposti).isEqualTo(yhteystieto.sahkoposti)
                prop(HakemusyhteystietoEntity::yhteyshenkilot).single().all {
                    prop(HakemusyhteyshenkiloEntity::id).isNotEqualTo(yhteyshenkilo.id)
                    prop(HakemusyhteyshenkiloEntity::hankekayttaja)
                        .isEqualTo(yhteyshenkilo.hankekayttaja)
                    prop(HakemusyhteyshenkiloEntity::tilaaja).isEqualTo(yhteyshenkilo.tilaaja)
                }
            }
        }

        @Test
        fun `uses the existing yhteyshenkilo when the muutosilmoitus references the same hankekayttaja`() {
            val hakemus = HakemusFactory.createEntity()
            val hankeId = hakemus.hanke.id
            val hakemusyhteystieto =
                HakemusyhteystietoFactory.createEntity(
                    application = hakemus,
                    rooli = ApplicationContactType.RAKENNUTTAJA,
                )
            val sally =
                HankeKayttajaFactory.createEntity(
                    hankeId = hankeId,
                    etunimi = "Sally",
                    sukunimi = "Stable",
                )
            val hakemusyhteyshenkilo =
                HakemusyhteyshenkiloFactory.createEntity(hakemusyhteystieto, sally, false)
            hakemusyhteystieto.yhteyshenkilot.add(hakemusyhteyshenkilo)
            hakemus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA] = hakemusyhteystieto

            val muutosilmoitus = MuutosilmoitusFactory.createEntity()
            val yhteystieto =
                MuutosilmoitusFactory.createYhteystietoEntity(
                    muutosilmoitus = muutosilmoitus,
                    rooli = ApplicationContactType.RAKENNUTTAJA,
                )
            val yhteyshenkilo =
                MuutosilmoitusFactory.createYhteyshenkiloEntity(yhteystieto, sally, false)
            yhteystieto.yhteyshenkilot.add(yhteyshenkilo)
            muutosilmoitus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA] = yhteystieto

            MuutosilmoitusService.mergeMuutosilmoitusToHakemus(muutosilmoitus, hakemus)

            assertThat(hakemus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA]).isNotNull().all {
                prop(HakemusyhteystietoEntity::yhteyshenkilot).single().all {
                    prop(HakemusyhteyshenkiloEntity::id).isNotEqualTo(yhteyshenkilo.id)
                    prop(HakemusyhteyshenkiloEntity::id).isEqualTo(hakemusyhteyshenkilo.id)
                    prop(HakemusyhteyshenkiloEntity::hankekayttaja)
                        .isEqualTo(yhteyshenkilo.hankekayttaja)
                    prop(HakemusyhteyshenkiloEntity::tilaaja).isEqualTo(yhteyshenkilo.tilaaja)
                }
            }
        }

        @Test
        fun `updates the yhteyshenkilo when the muutosilmoitus references the same hankekayttaja`() {
            val hakemus = HakemusFactory.createEntity()
            val hankeId = hakemus.hanke.id
            val hakemusyhteystieto =
                HakemusyhteystietoFactory.createEntity(
                    application = hakemus,
                    rooli = ApplicationContactType.RAKENNUTTAJA,
                )
            val tapio =
                HankeKayttajaFactory.createEntity(
                    hankeId = hankeId,
                    etunimi = "Tapio",
                    sukunimi = "Tilaaja",
                )
            val hakemusyhteyshenkilo =
                HakemusyhteyshenkiloFactory.createEntity(hakemusyhteystieto, tapio, false)
            hakemusyhteystieto.yhteyshenkilot.add(hakemusyhteyshenkilo)
            hakemus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA] = hakemusyhteystieto

            val muutosilmoitus = MuutosilmoitusFactory.createEntity()
            val yhteystieto =
                MuutosilmoitusFactory.createYhteystietoEntity(
                    muutosilmoitus = muutosilmoitus,
                    rooli = ApplicationContactType.RAKENNUTTAJA,
                )
            val yhteyshenkilo =
                MuutosilmoitusFactory.createYhteyshenkiloEntity(yhteystieto, tapio, true)
            yhteystieto.yhteyshenkilot.add(yhteyshenkilo)
            muutosilmoitus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA] = yhteystieto

            MuutosilmoitusService.mergeMuutosilmoitusToHakemus(muutosilmoitus, hakemus)

            assertThat(hakemus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA]).isNotNull().all {
                prop(HakemusyhteystietoEntity::yhteyshenkilot).single().all {
                    prop(HakemusyhteyshenkiloEntity::id).isNotEqualTo(yhteyshenkilo.id)
                    prop(HakemusyhteyshenkiloEntity::id).isEqualTo(hakemusyhteyshenkilo.id)
                    prop(HakemusyhteyshenkiloEntity::hankekayttaja)
                        .isEqualTo(yhteyshenkilo.hankekayttaja)
                    prop(HakemusyhteyshenkiloEntity::tilaaja).isEqualTo(true)
                }
            }
        }

        @Test
        fun `removes an yhteyshenkilo from the hakemus when they have been removed from the muutosilmoitus`() {
            val hakemus = HakemusFactory.createEntity()
            val hankeId = hakemus.hanke.id
            val hakemusyhteystieto =
                HakemusyhteystietoFactory.createEntity(
                    application = hakemus,
                    rooli = ApplicationContactType.RAKENNUTTAJA,
                )
            val sally =
                HankeKayttajaFactory.createEntity(
                    id = UUID.fromString("2b4bff3f-40c0-4a02-b320-12a8636b467f"),
                    hankeId = hankeId,
                    etunimi = "Sally",
                    sukunimi = "Stable",
                )
            val danny =
                HankeKayttajaFactory.createEntity(
                    id = UUID.fromString("0f703fc1-7b96-4c9f-81ae-545dceb1e8cd"),
                    hankeId = hankeId,
                    etunimi = "Danny",
                    sukunimi = "Deletable",
                )
            val hakemusyhteyshenkilot =
                listOf(sally, danny).map {
                    HakemusyhteyshenkiloFactory.createEntity(hakemusyhteystieto, it, false)
                }
            hakemusyhteyshenkilot.forEach { hakemusyhteystieto.yhteyshenkilot.add(it) }
            hakemus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA] = hakemusyhteystieto

            val muutosilmoitus = MuutosilmoitusFactory.createEntity()
            val yhteystieto =
                MuutosilmoitusFactory.createYhteystietoEntity(
                    muutosilmoitus = muutosilmoitus,
                    rooli = ApplicationContactType.RAKENNUTTAJA,
                )
            val yhteyshenkilo =
                MuutosilmoitusFactory.createYhteyshenkiloEntity(yhteystieto, sally, true)
            yhteystieto.yhteyshenkilot.add(yhteyshenkilo)
            muutosilmoitus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA] = yhteystieto

            MuutosilmoitusService.mergeMuutosilmoitusToHakemus(muutosilmoitus, hakemus)

            assertThat(hakemus.yhteystiedot[ApplicationContactType.RAKENNUTTAJA]).isNotNull().all {
                prop(HakemusyhteystietoEntity::yhteyshenkilot).single().all {
                    prop(HakemusyhteyshenkiloEntity::id).isNotEqualTo(yhteyshenkilo.id)
                    prop(HakemusyhteyshenkiloEntity::id).isEqualTo(hakemusyhteyshenkilot[0].id)
                    prop(HakemusyhteyshenkiloEntity::hankekayttaja)
                        .isEqualTo(yhteyshenkilo.hankekayttaja)
                    prop(HakemusyhteyshenkiloEntity::tilaaja).isEqualTo(true)
                }
            }
        }
    }
}

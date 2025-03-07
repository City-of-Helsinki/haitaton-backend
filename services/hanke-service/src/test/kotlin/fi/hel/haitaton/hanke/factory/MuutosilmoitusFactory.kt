package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.hakemus.HakemusEntityData
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoituksenYhteyshenkiloEntity
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoituksenYhteyshenkiloRepository
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoituksenYhteystietoEntity
import fi.hel.haitaton.hanke.muutosilmoitus.Muutosilmoitus
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusEntity
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusRepository
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusService
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class MuutosilmoitusFactory(
    private val muutosilmoitusService: MuutosilmoitusService,
    private val muutosilmoitusRepository: MuutosilmoitusRepository,
    private val yhteyshenkiloRepository: MuutosilmoituksenYhteyshenkiloRepository,
) {
    private fun builder(muutosilmoitusEntity: MuutosilmoitusEntity): MuutosilmoitusBuilder =
        MuutosilmoitusBuilder(
            muutosilmoitusEntity,
            muutosilmoitusService,
            muutosilmoitusRepository,
            yhteyshenkiloRepository,
        )

    fun builder(hakemus: HakemusEntity): MuutosilmoitusBuilder {
        val entity =
            MuutosilmoitusEntity(
                hakemusId = hakemus.id,
                hakemusData = hakemus.hakemusEntityData,
                sent = null,
            )
        entity.yhteystiedot =
            hakemus.yhteystiedot
                .mapValues { (_, hakemusyhteystieto) ->
                    val muutosilmoituksenYhteystieto =
                        MuutosilmoituksenYhteystietoEntity(
                            tyyppi = hakemusyhteystieto.tyyppi,
                            rooli = hakemusyhteystieto.rooli,
                            nimi = hakemusyhteystieto.nimi,
                            sahkoposti = hakemusyhteystieto.sahkoposti,
                            puhelinnumero = hakemusyhteystieto.puhelinnumero,
                            registryKey = hakemusyhteystieto.registryKey,
                            muutosilmoitus = entity,
                            yhteyshenkilot = mutableListOf(),
                        )
                    muutosilmoituksenYhteystieto.yhteyshenkilot =
                        hakemusyhteystieto.yhteyshenkilot
                            .map {
                                MuutosilmoituksenYhteyshenkiloEntity(
                                    yhteystieto = muutosilmoituksenYhteystieto,
                                    hankekayttaja = it.hankekayttaja,
                                    tilaaja = it.tilaaja,
                                )
                            }
                            .toMutableList()
                    muutosilmoituksenYhteystieto
                }
                .toMutableMap()
        return builder(entity)
    }

    companion object {
        val DEFAULT_ID: UUID = UUID.fromString("7df81277-2e36-4082-8687-0421c20d341e")

        fun create(
            id: UUID = TaydennysFactory.DEFAULT_ID,
            hakemusType: ApplicationType = ApplicationType.EXCAVATION_NOTIFICATION,
            hakemusId: Long = 1L,
            sent: OffsetDateTime? = null,
            hakemusData: HakemusData = HakemusFactory.createHakemusData(hakemusType),
        ) = Muutosilmoitus(id, hakemusId, sent, hakemusData)

        fun createEntity(
            id: UUID = TaydennysFactory.DEFAULT_ID,
            hakemusId: Long = ApplicationFactory.DEFAULT_APPLICATION_ID,
            sent: OffsetDateTime? = null,
            hakemusData: HakemusEntityData =
                ApplicationFactory.createBlankExcavationNotificationData(),
            yhteystiedot: Map<ApplicationContactType, MuutosilmoituksenYhteystietoEntity> = mapOf(),
        ) = MuutosilmoitusEntity(id, hakemusId, sent, hakemusData, yhteystiedot.toMutableMap())
    }
}

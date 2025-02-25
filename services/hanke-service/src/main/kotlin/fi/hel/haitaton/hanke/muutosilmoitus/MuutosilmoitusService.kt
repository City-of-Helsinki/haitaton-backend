package fi.hel.haitaton.hanke.muutosilmoitus

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.hakemus.WrongHakemusTypeException
import fi.hel.haitaton.hanke.logging.MuutosilmoitusLoggingService
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class MuutosilmoitusService(
    private val muutosilmoitusRepository: MuutosilmoitusRepository,
    private val hakemusRepository: HakemusRepository,
    private val loggingService: MuutosilmoitusLoggingService,
) {

    @Transactional(readOnly = true)
    fun find(hakemusId: Long): Muutosilmoitus? =
        muutosilmoitusRepository.findByHakemusId(hakemusId)?.toDomain()

    @Transactional
    fun create(id: Long, currentUserId: String): Muutosilmoitus {
        val hakemus = hakemusRepository.findOneById(id) ?: throw HakemusNotFoundException(id)

        logger.info { "Creating a muutosilmoitus. ${hakemus.logString()}" }

        val allowedStatuses =
            listOf(ApplicationStatus.DECISION, ApplicationStatus.OPERATIONAL_CONDITION)
        if (hakemus.alluStatus !in allowedStatuses) {
            throw HakemusInWrongStatusException(hakemus, hakemus.alluStatus, allowedStatuses)
        }
        if (hakemus.applicationType != ApplicationType.EXCAVATION_NOTIFICATION) {
            throw WrongHakemusTypeException(
                hakemus,
                hakemus.applicationType,
                listOf(ApplicationType.EXCAVATION_NOTIFICATION),
            )
        }

        val saved = createFromHakemus(hakemus).toDomain()
        loggingService.logCreate(saved, currentUserId)
        return saved
    }

    private fun createFromHakemus(hakemus: HakemusEntity): MuutosilmoitusEntity {
        val muutosilmoitus =
            muutosilmoitusRepository.save(
                MuutosilmoitusEntity(
                    hakemusId = hakemus.id,
                    sent = null,
                    hakemusData = hakemus.hakemusEntityData,
                )
            )

        muutosilmoitus.yhteystiedot.putAll(
            hakemus.yhteystiedot.mapValues { createYhteystieto(it.value, muutosilmoitus) }
        )

        return muutosilmoitus
    }

    companion object {
        private fun createYhteystieto(
            yhteystieto: HakemusyhteystietoEntity,
            muutosilmoitus: MuutosilmoitusEntity,
        ): MuutosilmoituksenYhteystietoEntity =
            MuutosilmoituksenYhteystietoEntity(
                    tyyppi = yhteystieto.tyyppi,
                    rooli = yhteystieto.rooli,
                    nimi = yhteystieto.nimi,
                    sahkoposti = yhteystieto.sahkoposti,
                    puhelinnumero = yhteystieto.puhelinnumero,
                    registryKey = yhteystieto.registryKey,
                    muutosilmoitus = muutosilmoitus,
                )
                .apply {
                    yhteyshenkilot.addAll(
                        yhteystieto.yhteyshenkilot.map { createYhteyshenkilo(it, this) }
                    )
                }

        private fun createYhteyshenkilo(
            yhteyshenkilo: HakemusyhteyshenkiloEntity,
            yhteystieto: MuutosilmoituksenYhteystietoEntity,
        ) =
            MuutosilmoituksenYhteyshenkiloEntity(
                yhteystieto = yhteystieto,
                hankekayttaja = yhteyshenkilo.hankekayttaja,
                tilaaja = yhteyshenkilo.tilaaja,
            )
    }
}

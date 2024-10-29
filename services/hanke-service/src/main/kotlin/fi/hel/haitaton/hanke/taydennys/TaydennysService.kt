package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.hakemus.HakemusIdentifier
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.logging.TaydennysLoggingService
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class TaydennysService(
    private val taydennyspyyntoRepository: TaydennyspyyntoRepository,
    private val taydennysRepository: TaydennysRepository,
    private val hakemusRepository: HakemusRepository,
    private val alluClient: AlluClient,
    private val loggingService: TaydennysLoggingService,
) {
    @Transactional(readOnly = true)
    fun findTaydennyspyynto(hakemusId: Long): Taydennyspyynto? =
        taydennyspyyntoRepository.findByApplicationId(hakemusId)?.toDomain()

    @Transactional(readOnly = true)
    fun findTaydennys(hakemusId: Long): Taydennys? =
        taydennysRepository.findByApplicationId(hakemusId)?.toDomain()

    @Transactional
    fun saveTaydennyspyyntoFromAllu(hakemus: HakemusIdentifier) {
        val request = alluClient.getInformationRequest(hakemus.alluid!!)

        val entity =
            TaydennyspyyntoEntity(
                applicationId = hakemus.id,
                alluId = request.informationRequestId,
                kentat =
                    request.fields
                        .associate { it.fieldKey to it.requestDescription }
                        .toMutableMap(),
            )

        taydennyspyyntoRepository.save(entity)
    }

    @Transactional
    fun create(hakemusId: Long, currentUserId: String): Taydennys {
        val taydennyspyynto =
            taydennyspyyntoRepository.findByApplicationId(hakemusId)
                ?: throw NoTaydennyspyyntoException(hakemusId)
        val hakemus = hakemusRepository.getReferenceById(hakemusId)

        if (hakemus.alluStatus != ApplicationStatus.WAITING_INFORMATION) {
            throw HakemusInWrongStatusException(
                hakemus,
                hakemus.alluStatus,
                listOf(ApplicationStatus.WAITING_INFORMATION),
            )
        }

        val saved = createFromHakemus(hakemus, taydennyspyynto).toDomain()
        loggingService.logCreate(saved, currentUserId)
        return saved
    }

    @Transactional
    fun createFromHakemus(
        hakemus: HakemusEntity,
        taydennyspyynto: TaydennyspyyntoEntity,
    ): TaydennysEntity {
        val taydennys =
            taydennysRepository.save(
                TaydennysEntity(
                    taydennyspyynto = taydennyspyynto,
                    hakemusData = hakemus.hakemusEntityData,
                ))

        taydennys.yhteystiedot.putAll(
            hakemus.yhteystiedot.mapValues { createYhteystieto(it.value, taydennys) })

        return taydennys
    }

    @Transactional
    fun removeTaydennyspyyntoIfItExists(application: HakemusEntity) {
        logger.info {
            "A hakemus has has entered handling. Checking if there's a täydennyspyyntö for the hakemus. ${application.logString()}"
        }

        taydennyspyyntoRepository.findByApplicationId(application.id)?.let {
            logger.info { "A täydennyspyyntö was found. Removing it." }
            taydennyspyyntoRepository.delete(it)

            if (application.alluStatus != ApplicationStatus.WAITING_INFORMATION) {
                logger.error {
                    "A hakemus moved to handling and it had a täydennyspyyntö, " +
                        "but the previous state was not 'HANDLING'. " +
                        "status=${application.alluStatus} ${application.logString()}"
                }
            }
        }
    }

    private fun createYhteystieto(
        yhteystieto: HakemusyhteystietoEntity,
        taydennys: TaydennysEntity,
    ): TaydennysyhteystietoEntity =
        TaydennysyhteystietoEntity(
                tyyppi = yhteystieto.tyyppi,
                rooli = yhteystieto.rooli,
                nimi = yhteystieto.nimi,
                sahkoposti = yhteystieto.sahkoposti,
                puhelinnumero = yhteystieto.puhelinnumero,
                registryKey = yhteystieto.registryKey,
                taydennys = taydennys,
            )
            .apply {
                yhteyshenkilot.addAll(
                    yhteystieto.yhteyshenkilot.map { createYhteyshenkilo(it, this) })
            }

    private fun createYhteyshenkilo(
        yhteyshenkilo: HakemusyhteyshenkiloEntity,
        yhteystieto: TaydennysyhteystietoEntity,
    ) =
        TaydennysyhteyshenkiloEntity(
            taydennysyhteystieto = yhteystieto,
            hankekayttaja = yhteyshenkilo.hankekayttaja,
            tilaaja = yhteyshenkilo.tilaaja,
        )
}

class NoTaydennyspyyntoException(hakemusId: Long) :
    RuntimeException("Application doesn't have an open taydennyspyynto. hakemusId=$hakemusId")

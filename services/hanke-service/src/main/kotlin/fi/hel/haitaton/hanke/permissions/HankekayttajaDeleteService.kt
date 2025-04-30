package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.email.RemovalFromHankeNotificationEmail
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.logging.HankeKayttajaLoggingService
import fi.hel.haitaton.hanke.logging.PermissionLoggingService
import java.util.UUID
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/** Split from [HankeKayttajaService] to avoid cyclic dependencies. */
@Service
class HankekayttajaDeleteService(
    private val hankekayttajaRepository: HankekayttajaRepository,
    private val hankeService: HankeService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val permissionRepository: PermissionRepository,
    private val hankeKayttajaLoggingService: HankeKayttajaLoggingService,
    private val permissionLoggingService: PermissionLoggingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val hankeRepository: HankeRepository,
) {
    @Transactional(readOnly = true)
    fun checkForDelete(kayttajaId: UUID): DeleteInfo {
        val kayttaja = getKayttaja(kayttajaId)
        val hanke =
            hankeService.loadHankeById(kayttaja.hankeId)
                ?: throw HankeKayttajaNotFoundException(kayttaja.id)

        val isOnlyOmistajanYhteyshenkilo =
            onlyOmistajanYhteyshenkiloIn(kayttaja, hanke).isNotEmpty()

        val (draftHakemukset, activeHakemukset) =
            getHakemuksetForKayttaja(kayttaja)
                .map { DeleteInfo.HakemusDetails(it) }
                .partition { it.alluStatus == null }

        return DeleteInfo(activeHakemukset, draftHakemukset, isOnlyOmistajanYhteyshenkilo)
    }

    @Transactional
    fun delete(kayttajaId: UUID, userId: String) {
        val kayttaja = getKayttaja(kayttajaId)
        val hanke =
            hankeService.loadHankeById(kayttaja.hankeId)
                ?: throw HankeKayttajaNotFoundException(kayttaja.id)
        val currentUser = hankeKayttajaService.getKayttajaByUserId(hanke.id, userId)!!

        if (isTheOnlyKaikkiOikeudetKayttaja(kayttaja)) {
            throw NoAdminRemainingException(hanke)
        }

        val offendingOmistajat = onlyOmistajanYhteyshenkiloIn(kayttaja, hanke)
        if (offendingOmistajat.isNotEmpty()) {
            throw OnlyOmistajaContactException(kayttajaId, offendingOmistajat.map { it.id!! })
        }

        val hakemukset = getHakemuksetForKayttaja(kayttajaId)
        val activeHakemukset = hakemukset.filter { it.alluStatus != null }
        if (activeHakemukset.isNotEmpty()) {
            throw HasActiveApplicationsException(kayttajaId, activeHakemukset.map { it.id })
        }

        val kayttajaBefore = kayttaja.toDomain()
        clearFromYhteystiedot(kayttajaId, hakemukset, hanke.id)

        kayttaja.permission?.also {
            logger.info {
                "Deleting permission for hankekayttaja ${kayttaja.id}. " +
                    "Permission: id=${it.id}, userId=${it.userId}, " +
                    "hankeId=${hanke.id}, kayttooikeustaso=${it.kayttooikeustaso}"
            }
            kayttaja.permission = null
            permissionRepository.delete(it)
            permissionLoggingService.logDelete(it.toDomain(), userId)
        }

        val kutsutut = hankekayttajaRepository.findByKutsujaId(kayttaja.id)
        kutsutut.forEach { it.kutsujaId = null }
        hankekayttajaRepository.saveAll(kutsutut)

        hankekayttajaRepository.delete(kayttaja)
        hankeKayttajaLoggingService.logDelete(kayttajaBefore, userId)

        applicationEventPublisher.publishEvent(
            RemovalFromHankeNotificationEmail(
                kayttaja.sahkoposti,
                hanke.hankeTunnus,
                hanke.nimi,
                currentUser.fullName(),
                currentUser.sahkoposti,
            )
        )
    }

    /**
     * Hibernate 6.6 seems to be more careful about the context checks when deleting entities.
     * Having the entities loaded anywhere in the context means that removing them causes
     * TransientObjectExceptions.
     *
     * Removing the yhteyshenkilot with the removed hankekayttaja from the respective collections of
     * the hakemukset and hanke since they have been loaded to the context earlier.
     *
     * There should be some combination of cascade annotations that does this, but I haven't gotten
     * any to work properly.
     */
    private fun clearFromYhteystiedot(
        kayttajaId: UUID,
        hakemukset: List<HakemusEntity>,
        hankeId: Int,
    ) {
        hakemukset.forEach { hakemus ->
            hakemus.yhteystiedot.forEach { (_, yhteystieto) ->
                yhteystieto.yhteyshenkilot.removeAll { it.hankekayttaja.id == kayttajaId }
            }
        }

        val hankeEntity = hankeRepository.getReferenceById(hankeId)
        hankeEntity.yhteystiedot.forEach { yhteystieto ->
            yhteystieto.yhteyshenkilot.removeAll { it.hankeKayttaja.id == kayttajaId }
        }
    }

    private fun isTheOnlyKaikkiOikeudetKayttaja(kayttaja: HankekayttajaEntity): Boolean {
        if (kayttaja.permission?.kayttooikeustaso != Kayttooikeustaso.KAIKKI_OIKEUDET) return false
        val adminCount =
            permissionRepository.findAllByHankeId(kayttaja.hankeId).count {
                it.kayttooikeustaso == Kayttooikeustaso.KAIKKI_OIKEUDET
            }
        return adminCount == 1
    }

    @Transactional(readOnly = true)
    fun getHakemuksetForKayttaja(kayttajaId: UUID): List<HakemusEntity> =
        getHakemuksetForKayttaja(getKayttaja(kayttajaId))

    private fun getKayttaja(kayttajaId: UUID): HankekayttajaEntity =
        hankekayttajaRepository.findByIdOrNull(kayttajaId)
            ?: throw HankeKayttajaNotFoundException(kayttajaId)

    private fun onlyOmistajanYhteyshenkiloIn(
        kayttaja: HankekayttajaEntity,
        hanke: Hanke,
    ): List<HankeYhteystieto> {
        return hanke.omistajat.filter {
            it.yhteyshenkilot.size == 1 && it.yhteyshenkilot.first().id == kayttaja.id
        }
    }

    private fun getHakemuksetForKayttaja(kayttaja: HankekayttajaEntity): List<HakemusEntity> =
        kayttaja.hakemusyhteyshenkilot
            .map { it.hakemusyhteystieto }
            .map { it.application }
            .distinctBy { it.id }

    data class DeleteInfo(
        val activeHakemukset: List<HakemusDetails>,
        val draftHakemukset: List<HakemusDetails>,
        val onlyOmistajanYhteyshenkilo: Boolean,
    ) {
        data class HakemusDetails(
            val nimi: String,
            val applicationIdentifier: String?,
            val alluStatus: ApplicationStatus?,
        ) {
            constructor(
                hakemus: HakemusEntity
            ) : this(
                hakemus.hakemusEntityData.name,
                hakemus.applicationIdentifier,
                hakemus.alluStatus,
            )
        }
    }
}

class OnlyOmistajaContactException(kayttajaId: UUID, yhteystietoId: List<Int>) :
    RuntimeException(
        "Hankekayttaja is the only contact for an omistaja. Cannot delete. " +
            "hankekayttajaId=$kayttajaId, yhteystietoIds=${yhteystietoId.joinToString(", ")}"
    )

class HasActiveApplicationsException(kayttajaId: UUID, hakemusIds: List<Long>) :
    RuntimeException(
        "Hankekayttaja is a contact in active applications. Cannot delete. " +
            "hankekayttajaId=$kayttajaId, applicationIds=${hakemusIds.joinToString(", ")}"
    )

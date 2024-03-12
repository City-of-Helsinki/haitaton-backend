package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.hakemus.Hakemus
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Split from [HankeKayttajaService] to avoid cyclic dependencies. */
@Service
class HankekayttajaDeleteService(
    private val hankekayttajaRepository: HankekayttajaRepository,
    private val hankeService: HankeService,
) {
    @Transactional(readOnly = true)
    fun checkForDelete(kayttajaId: UUID): DeleteInfo {
        val kayttaja = getKayttaja(kayttajaId)

        val isOnlyOmistajanYhteyshenkilo = onlyOmistajanYhteyshenkiloIn(kayttaja).isNotEmpty()

        val (draftHakemukset, activeHakemukset) =
            getHakemuksetForKayttaja(kayttaja)
                .map { DeleteInfo.HakemusDetails(it) }
                .partition { it.alluStatus == null }

        return DeleteInfo(activeHakemukset, draftHakemukset, isOnlyOmistajanYhteyshenkilo)
    }

    @Transactional(readOnly = true)
    fun getHakemuksetForKayttaja(kayttajaId: UUID): List<Hakemus> =
        getHakemuksetForKayttaja(getKayttaja(kayttajaId))

    private fun getKayttaja(kayttajaId: UUID): HankekayttajaEntity =
        hankekayttajaRepository.findByIdOrNull(kayttajaId)
            ?: throw HankeKayttajaNotFoundException(kayttajaId)

    private fun onlyOmistajanYhteyshenkiloIn(
        kayttaja: HankekayttajaEntity,
    ): List<HankeYhteystieto> {
        val hanke =
            hankeService.loadHankeById(kayttaja.hankeId)
                ?: throw HankeKayttajaNotFoundException(kayttaja.id)
        return hanke.omistajat.filter {
            it.yhteyshenkilot.size == 1 && it.yhteyshenkilot.first().id == kayttaja.id
        }
    }

    private fun getHakemuksetForKayttaja(kayttaja: HankekayttajaEntity): List<Hakemus> =
        kayttaja.hakemusyhteyshenkilot
            .map { it.hakemusyhteystieto }
            .map { it.application }
            .map { it.toHakemus() }

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
                hakemus: Hakemus
            ) : this(
                hakemus.applicationData.name,
                hakemus.applicationIdentifier,
                hakemus.alluStatus,
            )
        }
    }
}

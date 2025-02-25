package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoituksenYhteyshenkiloRepository
import fi.hel.haitaton.hanke.muutosilmoitus.Muutosilmoitus
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusEntity
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusRepository
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusService

class MuutosilmoitusBuilder(
    private var muutosilmoitusEntity: MuutosilmoitusEntity,
    private val muutosilmoitusService: MuutosilmoitusService,
    private val muutosilmoitusRepository: MuutosilmoitusRepository,
    private val yhteyshenkiloRepository: MuutosilmoituksenYhteyshenkiloRepository,
) {

    fun save(): Muutosilmoitus = muutosilmoitusService.find(saveEntity().hakemusId)!!

    fun saveEntity(): MuutosilmoitusEntity {
        val savedMuutosilmoitus = muutosilmoitusRepository.save(muutosilmoitusEntity)
        savedMuutosilmoitus.yhteystiedot.forEach { (_, yhteystieto) ->
            yhteystieto.yhteyshenkilot.forEach { yhteyshenkilo ->
                yhteyshenkiloRepository.save(yhteyshenkilo)
            }
        }
        return savedMuutosilmoitus
    }
}

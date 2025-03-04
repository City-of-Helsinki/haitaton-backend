package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.hakemus.HakemusEntityData
import fi.hel.haitaton.hanke.hakemus.Hakemusalue
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusalue
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusEntityData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusEntityData
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoituksenYhteyshenkiloRepository
import fi.hel.haitaton.hanke.muutosilmoitus.Muutosilmoitus
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusEntity
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusRepository
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusService
import java.security.InvalidParameterException
import java.time.OffsetDateTime

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

    fun withAreas(areas: List<Hakemusalue>) =
        updateApplicationData(
            {
                if (areas.any { it !is JohtoselvitysHakemusalue }) throw InvalidParameterException()
                copy(areas = areas.filterIsInstance<JohtoselvitysHakemusalue>())
            },
            {
                if (areas.any { it !is KaivuilmoitusAlue }) throw InvalidParameterException()
                copy(areas = areas.filterIsInstance<KaivuilmoitusAlue>())
            },
        )

    fun withWorkDescription(description: String) =
        updateApplicationData(
            { copy(workDescription = description) },
            { copy(workDescription = description) },
        )

    fun withAdditionalInfo(info: String) =
        updateApplicationData({ invalidHakemusType() }, { copy(additionalInfo = info) })

    fun withSent(sent: OffsetDateTime?): MuutosilmoitusBuilder {
        muutosilmoitusEntity.sent = sent
        return this
    }

    fun withoutYhteystiedot(): MuutosilmoitusBuilder {
        muutosilmoitusEntity.yhteystiedot = mutableMapOf()
        return this
    }

    private fun updateApplicationData(
        onCableReport: JohtoselvityshakemusEntityData.() -> JohtoselvityshakemusEntityData,
        onExcavationNotification: KaivuilmoitusEntityData.() -> KaivuilmoitusEntityData,
    ): MuutosilmoitusBuilder = apply {
        muutosilmoitusEntity.hakemusData =
            when (val data = muutosilmoitusEntity.hakemusData) {
                is JohtoselvityshakemusEntityData -> {
                    data.onCableReport()
                }
                is KaivuilmoitusEntityData -> {
                    data.onExcavationNotification()
                }
            }
    }

    private fun HakemusEntityData.invalidHakemusType(): Nothing {
        throw InvalidParameterException("Not available for hakemus type $applicationType.")
    }
}

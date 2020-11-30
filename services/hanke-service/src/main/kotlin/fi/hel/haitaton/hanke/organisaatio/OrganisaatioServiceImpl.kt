package fi.hel.haitaton.hanke.organisaatio

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger { }

@Service
class OrganisaatioServiceImpl(@Autowired val organisaatioRepository: OrganisaatioRepository) : OrganisaatioService {

    override fun getOrganisaatiot(): Iterable<Organisaatio> {
        logger.info { "fetching organizations" }
        val entities = organisaatioRepository.findAllByOrderByNimiAsc()
        return entities.map { createdOrganisaatioDomainObjectFromEntity(it) }
    }

    internal fun createdOrganisaatioDomainObjectFromEntity(organisaatioEntity: OrganisaatioEntity): Organisaatio {
        val o = Organisaatio(
            organisaatioEntity.id,
            organisaatioEntity.organisaatioTunnus,
            organisaatioEntity.nimi

        )
        return o
    }
}

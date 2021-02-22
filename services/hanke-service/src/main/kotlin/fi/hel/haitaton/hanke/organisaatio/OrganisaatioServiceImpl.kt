package fi.hel.haitaton.hanke.organisaatio

import org.springframework.beans.factory.annotation.Autowired

class OrganisaatioServiceImpl(@Autowired val organisaatioRepository: OrganisaatioRepository) : OrganisaatioService {

    override fun getOrganisaatiot(): Iterable<Organisaatio> {
        val entities = organisaatioRepository.findAllByOrderByNimiAsc()
        return entities.map { createdOrganisaatioDomainObjectFromEntity(it) }
    }

    private fun createdOrganisaatioDomainObjectFromEntity(organisaatioEntity: OrganisaatioEntity): Organisaatio {
        return Organisaatio(
            organisaatioEntity.id,
            organisaatioEntity.organisaatioTunnus,
            organisaatioEntity.nimi
        )
    }
}

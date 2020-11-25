package fi.hel.haitaton.hanke.organisaatio

import fi.hel.haitaton.hanke.*
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

private val logger = KotlinLogging.logger { }

@Service
class OrganisaatioServiceImpl(@Autowired val organisaatioRepository: OrganisaatioRepository) : OrganisaatioService {

    override fun getOrganisaatiot(): Iterable<Organisaatio> {
        logger.info { "fetching organizations" }
        val entities = organisaatioRepository.findAllByOrderByIdAsc() ?: throw OrganisaatioNotFoundException()
        return entities.map { createdOrganisaatioDomainObjectFromEntity(it) }
    }

    internal fun createdOrganisaatioDomainObjectFromEntity(organisaatioEntity: OrganisaatioEntity): Organisaatio {
        val o = Organisaatio(
            organisaatioEntity.id,
            organisaatioEntity.organisaatioTunnus,
            organisaatioEntity.nimi,
            if (organisaatioEntity.createdAt != null) ZonedDateTime.of(organisaatioEntity.createdAt, TZ_UTC) else null,
            if (organisaatioEntity.modifiedAt != null) ZonedDateTime.of(organisaatioEntity.modifiedAt, TZ_UTC) else null,

        )
        return o
    }
}

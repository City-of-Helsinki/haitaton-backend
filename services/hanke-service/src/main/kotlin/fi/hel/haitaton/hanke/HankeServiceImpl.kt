package fi.hel.haitaton.hanke

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

private val logger = KotlinLogging.logger { }

@Service
class HankeServiceImpl (@Autowired val hankeRepository: HankeRepository) : HankeService {

    override fun loadHanke(hankeId: String): Hanke? {
        // TODO: Remove this special case after other stuff works; for testing purposes
        if (hankeId.equals("SMTGEN_12"))
            return Hanke("", true, "Hämeentien perusparannus ja katuvalot", ZonedDateTime.now(), ZonedDateTime.now(), "", 1)

        val entity = hankeRepository.findByHankeTunnus(hankeId) ?: throw HankeNotFoundException(hankeId)
        val hanke = createHankeDomainObjectFromEntity(entity)
        return hanke
    }

    override fun createHanke(hanke: Hanke): Hanke {
        // TODO: Once we have a proper service for creating hanke-tunnus,
        // only one save call is needed here. I.e. get a new tunnus, put it into
        // both the domain object and the entity, save the entity, return

        // Create the entity object and save it (first time) to get db-id
        val entity = HankeEntity()
        copyNonNullHankeFieldsToEntity(hanke, entity)
        hankeRepository.save(entity)
        // TEMPORARY: Get the db-id and create hankeTunnus with it, put it in both objects, save again
        val id = entity.id
        val tunnus = "SMTGEN2_$id"
        entity.hankeTunnus = tunnus
        hanke.hankeId = tunnus
        hankeRepository.save(entity) // ... Just to save that hankeTunnus

        return hanke
    }

    override fun updateHanke(hanke: Hanke): Hanke {
        if (hanke.hankeId == null)
            error("Somehow got here with hanke without hanke-tunnus")
        val entity = hankeRepository.findByHankeTunnus(hanke.hankeId!!) ?: throw HankeNotFoundException(hanke.hankeId)
        copyNonNullHankeFieldsToEntity(hanke, entity)
        entity.modifiedAt = ZonedDateTime.now(TZ_UTC).toLocalDateTime() // TODO: check that it is UTC
        // TODO: modifiedAt to domain once/if domain gets that field
        hankeRepository.save(entity)

        // TODO: could also just copy all fields from the entity to the existing domain object,
        // but would need to create that helper method first.
        return createHankeDomainObjectFromEntity(entity)
    }

    fun createHankeDomainObjectFromEntity(hankeEntity: HankeEntity): Hanke {
        // TODO: check if the SQL date things could be converted newer types in the database/mapping,
        // to avoid these conversions. (Note though that we do not really need the time part.)
        val h = Hanke(
                hankeEntity.hankeTunnus,
                hankeEntity.isYKTHanke,
                hankeEntity.name,
                hankeEntity.startDate?.atStartOfDay(TZ_UTC),
                hankeEntity.endDate?.atStartOfDay(TZ_UTC),
                hankeEntity.owner ?: "",
                hankeEntity.phase?.toIntOrNull()
        )
        return h
    }

    fun copyNonNullHankeFieldsToEntity(hanke: Hanke, entity: HankeEntity) {
        hanke.isYKTHanke?.let { entity.isYKTHanke = hanke.isYKTHanke }
        hanke.name?.let { entity.name = hanke.name }
        hanke.startDate?.let { entity.startDate = hanke.startDate?.toLocalDate() }
        hanke.endDate?.let { entity.endDate = hanke.endDate?.toLocalDate() }
        entity.owner = hanke.owner
        hanke.phase?.let { entity.phase = hanke.phase.toString() }
    }

}

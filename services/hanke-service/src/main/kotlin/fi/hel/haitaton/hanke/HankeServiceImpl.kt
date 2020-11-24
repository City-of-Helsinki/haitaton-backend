package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

private val logger = KotlinLogging.logger { }

@Service
class HankeServiceImpl (@Autowired val hankeRepository: HankeRepository) : HankeService {

    // TODO:
    /*
       - a structure to return a list of references to Hanke and their savetypes (i.e. can contain up to 3 saves for the same hanke)
       -- there could be all 3 simultaneously, and any combination ...
       -- UI should show about autosave if (autosave exists and is newer than draft/submit and is different from draft/submit);
           it should show either a draft or submit, whichever is newest (if there is a draft, it should be newer than submit, since submit deletes drafts and autosaves)
       - a method to query all hankkeet for a given user..
            findAllHankeForUser(userId: String): SpecialListOf ...
       - a method to query the current savestates for given hanke
           getHankeSaves(hankeId: Long): SpecialListOf ...
            getHankeSaves(hankeTunnus: String): SpecialListOf ...

       - change loadHanke(tunnus) to return latest non-autosave (either draft or submit)
       - loadHanke(tunnus, savetype)
       - loadHanke(id, savetype)
     */

    override fun loadHanke(hankeTunnus: String): Hanke? {
        // TODO: Remove this special case after other stuff works; for testing purposes
        if (hankeTunnus.equals("SMTGEN_12"))
            return Hanke(0, "", true, "Hämeentien perusparannus ja katuvalot", "Lorem ipsum dolor sit amet...",
                    getCurrentTimeUTC(), getCurrentTimeUTC(), "OHJELMOINTI",
                    1, "0", getCurrentTimeUTC(), "0", getCurrentTimeUTC(), SaveType.DRAFT)

        // TODO: Find out all savetype matches and return the more recent draft vs. submit.
        val entity = hankeRepository.findByHankeTunnus(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)
        return createHankeDomainObjectFromEntity(entity)
    }

    /**
     * @return a new Hanke instance with the added and possibly modified values.
     */
    override fun createHanke(hanke: Hanke): Hanke {
        // TODO: Once we have a proper service for creating hanke-tunnus,
        //   only one save call is needed here. I.e. get a new tunnus, put it into
        //   both the domain object and the entity, save the entity, return
        // TODO: Only create that hanke-tunnus if a specific set of fields are non-empty/set.
        //   For now, hanke-tunnus is created as soon as this function is called, even for fully empty data.

        // Create the entity object and save it (first time) to get db-id
        val entity = HankeEntity()
        copyNonNullHankeFieldsToEntity(hanke, entity)
        // Special fields; handled "manually".. TODO: see if some framework could handle (some of) these for us automatically
        entity.version = 0
        // TODO: will need proper stuff derived from the logged in user.
        entity.createdByUserId =  1
        entity.createdAt = getCurrentTimeUTCAsLocalTime()
        entity.modifiedByUserId = null
        entity.modifiedAt = null

        logger.info {
            // TODO: once the hanke-tunnus gets its own service, this logging line gets more useful
            //"Saving  Hanke ${hanke.hankeId}: ${hanke.toJsonString()}"
            "Saving Hanke step 1 for: ${hanke.toJsonString()}"
        }
        hankeRepository.save(entity)
        // TODO: For now, get the db-id and create hankeTunnus with it, put it in both objects, save again
        val id = entity.id
        val tunnus = "SMTGEN2_$id"
        entity.hankeTunnus = tunnus
        // TODO: once the hanke-tunnus gets its own service, this logging and the second save becomes obsolete.
        logger.info {
            "Saving  Hanke step 2 for: ${hanke.hankeTunnus}"
        }
        hankeRepository.save(entity) // ... Just to save that newly created hankeTunnus
        logger.info {
            "Saved Hanke ${hanke.hankeTunnus}"
        }

        // Creating a new domain object for the return value; it will have the updated values from the database,
        // e.g. the main date values truncated to midnight, and the added id and hanketunnus.
        return createHankeDomainObjectFromEntity(entity)
    }

    override fun updateHanke(hanke: Hanke): Hanke {
        if (hanke.hankeTunnus == null)
            error("Somehow got here with hanke without hanke-tunnus")

        val entity = hankeRepository.findByHankeTunnus(hanke.hankeTunnus!!) ?: throw HankeNotFoundException(hanke.hankeTunnus)
        copyNonNullHankeFieldsToEntity(hanke, entity)
        // Special fields; handled "manually".. TODO: see if some framework could handle (some of) these for us automatically
        entity.version = entity.version?.inc() ?: 1
        // (Not changing creator/time fields.)
        // TODO: will need proper stuff derived from the logged in user.
        entity.modifiedByUserId = 1
        entity.modifiedAt = getCurrentTimeUTCAsLocalTime()

        logger.info {
            "Saving  Hanke ${hanke.hankeTunnus}: ${hanke.toJsonString()}"
        }
        hankeRepository.save(entity)
        logger.info {
            "Saved  Hanke ${hanke.hankeTunnus}"
        }

        // Creating a new domain object for the return value; it will have the updated values from the database,
        // e.g. the main date values truncated to midnight.
        return createHankeDomainObjectFromEntity(entity)
    }

    companion object Converters {
        internal fun createHankeDomainObjectFromEntity(hankeEntity: HankeEntity): Hanke {
            // TODO: check if the SQL date things could be converted to newer types in the database/mapping,
            // to avoid these conversions. (Note though that we do not really need the time part.)
            val h = Hanke(
                    hankeEntity.id,

                    hankeEntity.hankeTunnus,
                    hankeEntity.onYKTHanke,
                    hankeEntity.nimi,
                    hankeEntity.kuvaus,
                    hankeEntity.alkuPvm?.atStartOfDay(TZ_UTC),
                    hankeEntity.loppuPvm?.atStartOfDay(TZ_UTC),
                    hankeEntity.vaihe,

                    hankeEntity.version,
                    // TODO: will need in future to actually fetch the username from another service.. (or whatever we choose to pass out here)
                    //   Do it below, outside this construction call.
                    hankeEntity.createdByUserId?.toString() ?: "",
                    // From UTC without timezone info to UTC with timezone info
                    if (hankeEntity.createdAt != null) ZonedDateTime.of(hankeEntity.createdAt, TZ_UTC) else null,
                    hankeEntity.modifiedByUserId?.toString(),
                    if (hankeEntity.modifiedAt != null) ZonedDateTime.of(hankeEntity.modifiedAt, TZ_UTC) else null,

                    hankeEntity.saveType
            )
            return h
        }

        /**
         * Does NOT copy the id and hankeTunnus fields because one is supposed to find
         * the HankeEntity instance from the database with those values, and after that,
         * the values are filled by the database and should not be changed.
         * Also, version, creatorUserId, createdAt, modifierUserId, modifiedAt, version are not
         * set here, as they are to be set internally, and depends on which operation
         * is being done.
         */
        internal fun copyNonNullHankeFieldsToEntity(hanke: Hanke, entity: HankeEntity) {
            hanke.onYKTHanke?.let { entity.onYKTHanke = hanke.onYKTHanke }
            hanke.nimi?.let { entity.nimi = hanke.nimi }
            hanke.kuvaus?.let { entity.kuvaus = hanke.kuvaus }
            // Assuming the incoming date, while being zoned date and time, is in UTC and time value can be simply dropped here.
            // Note, .toLocalDate() does not do any time zone conversion.
            hanke.alkuPvm?.let { entity.alkuPvm = hanke.alkuPvm?.toLocalDate() }
            hanke.loppuPvm?.let { entity.loppuPvm = hanke.loppuPvm?.toLocalDate() }
            hanke.vaihe?.let { entity.vaihe = hanke.vaihe }

            hanke.saveType?.let { entity.saveType = hanke.saveType }
        }
    }
}

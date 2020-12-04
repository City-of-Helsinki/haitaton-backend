package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto

import mu.KotlinLogging
import java.time.ZonedDateTime

private val logger = KotlinLogging.logger { }

class HankeServiceImpl(private val hankeRepository: HankeRepository) : HankeService {


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

       - createHanke can do either autosave or draft, not submit (not enough data for that)
       - updateHanke can do any save mode.

       - change loadHanke(tunnus) to return latest non-autosave (either draft or submit)
       - loadHanke(tunnus, savetype)
       - loadHanke(id, savetype)
     */

    override fun loadHanke(hankeTunnus: String): Hanke {
        // TODO: Find out all savetype matches and return the more recent draft vs. submit.
        val entity = hankeRepository.findByHankeTunnus(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        if (entity.id == null) {
            throw DatabaseStateException(hankeTunnus)
        }

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

        // TODO: will need proper stuff derived from the logged in user.
        val userid = 1

        // Create the entity object and save it (first time) to get db-id
        val entity = HankeEntity()
        copyNonNullHankeFieldsToEntity(hanke, entity)
        copyYhteystietosToEntity(hanke, entity, userid)
        // Special fields; handled "manually".. TODO: see if some framework could handle (some of) these for us automatically
        entity.version = 0
        entity.createdByUserId = userid
        entity.createdAt = getCurrentTimeUTCAsLocalTime()
        entity.modifiedByUserId = null
        entity.modifiedAt = null

        logger.info {
            // TODO: once the hanke-tunnus gets its own service, this logging line gets more useful
            //"Saving  Hanke ${entity.hankeTunnus}: ${hanke.toJsonString()}"
            "Saving Hanke step 1 for: ${hanke.toJsonString()}"
        }
        hankeRepository.save(entity)
        // TODO: For now, get the db-id and create hankeTunnus with it, put it in both objects, save again
        val id = entity.id
        val tunnus = "SMTGEN2_$id"
        entity.hankeTunnus = tunnus
        // TODO: once the hanke-tunnus gets its own service, this logging and the second save becomes obsolete.
        logger.info {
            "Saving  Hanke step 2 for: ${entity.hankeTunnus}"
        }

        hankeRepository.save(entity)
        logger.info {
            "Saved Hanke ${entity.hankeTunnus}"
        }

        // Creating a new domain object for the return value; it will have the updated values from the database,
        // e.g. the main date values truncated to midnight, and the added id and hanketunnus.
        return createHankeDomainObjectFromEntity(entity)
    }

    override fun updateHanke(hanke: Hanke): Hanke {
        if (hanke.hankeTunnus == null)
            error("Somehow got here with hanke without hanke-tunnus")

        // Both checks that the hanke already exists, and get its old fields to transfer data into
        val entity = hankeRepository.findByHankeTunnus(hanke.hankeTunnus!!)
                ?: throw HankeNotFoundException(hanke.hankeTunnus)
        // TODO: will need proper stuff derived from the logged in user.
        val userid = 1
        // Transfer field values from domain object to entity object, and set relevant audit fields:
        copyNonNullHankeFieldsToEntity(hanke, entity)
        copyYhteystietosToEntity(hanke, entity, userid)
        // Special fields; handled "manually".. TODO: see if some framework could handle (some of) these for us automatically
        entity.version = entity.version?.inc() ?: 1
        // (Not changing createdBy/At fields.)
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
            val h = Hanke(
                    hankeEntity.id,
                    hankeEntity.hankeTunnus,
                    hankeEntity.onYKTHanke,
                    hankeEntity.nimi,
                    hankeEntity.kuvaus,
                    hankeEntity.alkuPvm?.atStartOfDay(TZ_UTC),
                    hankeEntity.loppuPvm?.atStartOfDay(TZ_UTC),
                    hankeEntity.vaihe,
                    hankeEntity.suunnitteluVaihe,

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
            createSeparateYhteystietolistsFromEntityData(h, hankeEntity)

            h.tyomaaKatuosoite = hankeEntity.tyomaaKatuosoite
            h.tyomaaTyyppi = hankeEntity.tyomaaTyyppi
            h.tyomaaKoko = hankeEntity.tyomaaKoko

            h.haittaAlkuPvm = hankeEntity.haittaAlkuPvm?.atStartOfDay(TZ_UTC)
            h.haittaLoppuPvm = hankeEntity.haittaLoppuPvm?.atStartOfDay(TZ_UTC)
            h.kaistaHaitta = hankeEntity.kaistaHaitta
            h.kaistaPituusHaitta = hankeEntity.kaistaPituusHaitta
            h.meluHaitta = hankeEntity.meluHaitta
            h.polyHaitta = hankeEntity.polyHaitta
            h.tarinaHaitta = hankeEntity.tarinaHaitta

            return h
        }

        /**
         * createSeparateYhteystietolistsFromEntityData splits entity's one list to three different contact information
         * lists and adds them for Hanke domain object
         */
        private fun createSeparateYhteystietolistsFromEntityData(hanke: Hanke, hankeEntity: HankeEntity) {

            hankeEntity.listOfHankeYhteystieto.forEach { hankeYhteysEntity ->
                val hankeYhteystieto = createHankeYhteystietoDomainObjectFromEntity(hankeYhteysEntity)

                if (hankeYhteysEntity.contactType == ContactType.OMISTAJA)
                    hanke.omistajat.add(hankeYhteystieto)
                if (hankeYhteysEntity.contactType == ContactType.TOTEUTTAJA)
                    hanke.toteuttajat.add(hankeYhteystieto)
                if (hankeYhteysEntity.contactType == ContactType.ARVIOIJA)
                    hanke.arvioijat.add(hankeYhteystieto)
            }
        }

        private fun createHankeYhteystietoDomainObjectFromEntity(hankeYhteystietoEntity: HankeYhteystietoEntity): HankeYhteystieto {
            var createdAt: ZonedDateTime? = null

            if (hankeYhteystietoEntity.createdAt != null)
                createdAt = ZonedDateTime.of(hankeYhteystietoEntity.createdAt, TZ_UTC)

            var modifiedAt: ZonedDateTime? = null
            if (hankeYhteystietoEntity.modifiedAt != null)
                modifiedAt = ZonedDateTime.of(hankeYhteystietoEntity.modifiedAt, TZ_UTC)

            return HankeYhteystieto(
                    id = hankeYhteystietoEntity.id,
                    etunimi = hankeYhteystietoEntity.etunimi,
                    sukunimi = hankeYhteystietoEntity.sukunimi,
                    email = hankeYhteystietoEntity.email,
                    puhelinnumero = hankeYhteystietoEntity.puhelinnumero,
                    organisaatioId = hankeYhteystietoEntity.organisaatioId,
                    organisaatioNimi = hankeYhteystietoEntity.organisaatioNimi,
                    osasto = hankeYhteystietoEntity.osasto,
                    createdBy = hankeYhteystietoEntity.createdByUserId?.toString(),
                    modifiedBy = hankeYhteystietoEntity.modifiedByUserId?.toString(),
                    createdAt = createdAt,
                    modifiedAt = modifiedAt
            )
        }
    }

    /**
     * Does NOT copy the id and hankeTunnus fields because one is supposed to find
     * the HankeEntity instance from the database with those values, and after that,
     * the values are filled by the database and should not be changed.
     * Also, version, createdByUserId, createdAt, modifiedByUserId, modifiedAt, version are not
     * set here, as they are to be set internally, and depends on which operation
     * is being done.
     */
    private fun copyNonNullHankeFieldsToEntity(hanke: Hanke, entity: HankeEntity) {
        hanke.onYKTHanke?.let { entity.onYKTHanke = hanke.onYKTHanke }
        hanke.nimi?.let { entity.nimi = hanke.nimi }
        hanke.kuvaus?.let { entity.kuvaus = hanke.kuvaus }
        // Assuming the incoming date, while being zoned date and time, is in UTC and time value can be simply dropped here.
        // Note, .toLocalDate() does not do any time zone conversion.
        hanke.alkuPvm?.let { entity.alkuPvm = hanke.alkuPvm?.toLocalDate() }
        hanke.loppuPvm?.let { entity.loppuPvm = hanke.loppuPvm?.toLocalDate() }
        hanke.vaihe?.let { entity.vaihe = hanke.vaihe }
        hanke.suunnitteluVaihe?.let { entity.suunnitteluVaihe = hanke.suunnitteluVaihe }

        hanke.saveType?.let { entity.saveType = hanke.saveType }
        hanke.tyomaaKatuosoite?.let { entity.tyomaaKatuosoite = hanke.tyomaaKatuosoite }
        entity.tyomaaTyyppi = hanke.tyomaaTyyppi
        hanke.tyomaaKoko?.let { entity.tyomaaKoko = hanke.tyomaaKoko }

        // Assuming the incoming date, while being zoned date and time, is in UTC and time value can be simply dropped here.
        // Note, .toLocalDate() does not do any time zone conversion.
        hanke.haittaAlkuPvm?.let { entity.haittaAlkuPvm = hanke.haittaAlkuPvm?.toLocalDate() }
        hanke.haittaLoppuPvm?.let { entity.haittaLoppuPvm = hanke.haittaLoppuPvm?.toLocalDate() }
        hanke.kaistaHaitta?.let { entity.kaistaHaitta = hanke.kaistaHaitta }
        hanke.kaistaPituusHaitta?.let { entity.kaistaPituusHaitta = hanke.kaistaPituusHaitta }
        hanke.meluHaitta?.let { entity.meluHaitta = hanke.meluHaitta }
        hanke.polyHaitta?.let { entity.polyHaitta = hanke.polyHaitta }
        hanke.tarinaHaitta?.let { entity.tarinaHaitta = hanke.tarinaHaitta }

    }

    /**
     * Transfer yhteystieto fields from domain to (new or existing) entity object,
     * combine the three lists into one list, and set the audit fields as relevant.
     */
    private fun copyYhteystietosToEntity(hanke: Hanke, entity: HankeEntity, userid: Int) {
        // Note, if the incoming data indicates it is an already saved yhteystieto (id-field is set), should try
        // to transfer the business fields to the same old corresponding entity. Pretty much a must in order to
        // preserve createdBy and createdAt field values without having to rely on the client-side to hold
        // the values for us (bad design), which would also require checks on those (to prevent tampering).

        // Create temporary id-to-existingyhteystieto -map
        val existingYTs: MutableMap<Int, HankeYhteystietoEntity> = mutableMapOf()
        for (existingYT in entity.listOfHankeYhteystieto) {
            if (existingYT.id == null) {
                throw DatabaseStateException("A persisted HankeYhteystietoEntity somehow missing id, Hanke id ${entity.id}")
            } else {
                existingYTs[existingYT.id!!] = existingYT
            }
        }

        // Check each incoming yhteystieto (from three lists) for being new or an update to existing one,
        // and add to the main entity's single list if necessary:
        processIncomingHankeYhteystietosOfSpecificTypeToEntity(hanke.omistajat, entity, ContactType.OMISTAJA, userid, existingYTs)
        processIncomingHankeYhteystietosOfSpecificTypeToEntity(hanke.arvioijat, entity, ContactType.ARVIOIJA, userid, existingYTs)
        processIncomingHankeYhteystietosOfSpecificTypeToEntity(hanke.toteuttajat, entity, ContactType.TOTEUTTAJA, userid, existingYTs)

        // If there is anything left in the existingYTs map, they have been removed in the incoming data,
        // so remove them from the entity's list (and thus from the database).
        // TODO: it should not be a list, but a "bag".  Their order does not matter, and removing things from the list
        // gets inefficient. Since there are so few entries, this crude solution works, for now.
        entity.listOfHankeYhteystieto.removeAll(existingYTs.values)
    }

    private fun processIncomingHankeYhteystietosOfSpecificTypeToEntity(
            listOfHankeYhteystiedot: List<HankeYhteystieto>, hankeEntity: HankeEntity, contactType: ContactType,
            userid: Int, existingYTs: MutableMap<Int, HankeYhteystietoEntity>) {

        for (hankeYht in listOfHankeYhteystiedot) {
            // Is the incoming YT new (does not have id, create new) or old (has id, update existing):
            if (hankeYht.id == null) {
                // New YT, check if it is fully empty; if anything set, create new one.
                // Note, validator should have enforced that at least the four main fields are all set (or none of them set).
                if (hankeYht.sukunimi.isNotBlank() || hankeYht.etunimi.isNotBlank()
                        || hankeYht.email.isNotBlank() || hankeYht.puhelinnumero.isNotBlank()
                        || !hankeYht.organisaatioNimi.isNullOrBlank() || !hankeYht.osasto.isNullOrBlank()) {
                    val hankeYhtEntity = HankeYhteystietoEntity(
                            contactType,
                            hankeYht.sukunimi,
                            hankeYht.etunimi,
                            hankeYht.email,
                            hankeYht.puhelinnumero,
                            hankeYht.organisaatioId,
                            hankeYht.organisaatioNimi,
                            hankeYht.osasto,

                            userid, // createdByUserId
                            getCurrentTimeUTCAsLocalTime(), // createdAt
                            null,
                            null,
                            null, // will be set by the database
                            hankeEntity) // reference back to parent hanke
                    hankeEntity.listOfHankeYhteystieto.add(hankeYhtEntity)
                }
            } else {
                // Should be an existing YT, so update the values from the incoming YT to the existing database YT entity.
                // If incoming YT has id set, it _should_ be among the existing YTs, or some kind of error has happened.
                val incomingId: Int = hankeYht.id!!
                val existingYT: HankeYhteystietoEntity? = existingYTs[incomingId]
                if (existingYT == null) {
                    // Some sort of error situation (e.g. simultaneous edits to the same hanke by someone else).
                    throw DatabaseStateException("A persisted HankeYhteystietoEntity somehow missing from the database. YT.id $incomingId, Hanke id ${hankeEntity.id}")
                } else {
                    // All found, so update existing entity fields, and remove the entry from the map
                    existingYT.sukunimi = hankeYht.sukunimi
                    existingYT.etunimi = hankeYht.etunimi
                    existingYT.email = hankeYht.email
                    existingYT.puhelinnumero = hankeYht.puhelinnumero
                    hankeYht.organisaatioId?.let { existingYT.organisaatioId = hankeYht.organisaatioId }
                    hankeYht.organisaatioNimi?.let { existingYT.organisaatioNimi = hankeYht.organisaatioNimi }
                    hankeYht.osasto?.let { existingYT.osasto = hankeYht.osasto }

                    // (Not changing createdBy/At fields)
                    existingYT.modifiedByUserId = userid
                    existingYT.modifiedAt = getCurrentTimeUTCAsLocalTime()
                    // (Not touching the id or hanke fields)

                    // No need to add the existing YT entity to the list; it is already in it.
                    // But, remove the corresponding entry from the map, so that in the end of the loop, the map reveals
                    // which entries have been removed in the incoming data.
                    existingYTs.remove(incomingId)
                }
            }
        }
    }

}

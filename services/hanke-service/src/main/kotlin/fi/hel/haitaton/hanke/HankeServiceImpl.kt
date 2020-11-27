package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.security.InvalidKeyException
import java.time.ZonedDateTime

private val logger = KotlinLogging.logger { }

@Service
class HankeServiceImpl(@Autowired val hankeRepository: HankeRepository,
                       @Autowired val hankeYhteystiedotRepository: HankeYhteystietoRepository) : HankeService {


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

    override fun loadHanke(hankeTunnus: String): Hanke? {

        // TODO: Find out all savetype matches and return the more recent draft vs. submit.
        val entity = hankeRepository.findByHankeTunnus(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        if (entity.id == null) {
            throw InvalidKeyException("Hanke id missing")
        }

        val listOfHankeYhteystiedot = entity.id?.let { hankeYhteystiedotRepository.findByHankeId(it) }
        entity.listOfHankeYhteystieto = listOfHankeYhteystiedot as MutableList<HankeYhteystietoEntity>?

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
        entity.createdByUserId = 1
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
        //TODO: maybe we need to check the transaction situation here before the subentity save?

        hankeRepository.save(entity) // ... Just to save that newly created hankeTunnus
        logger.info {
            "Saved Hanke ${entity.hankeTunnus}"
        }

        //createEntityFromHankeYhteystiedotDomainObject(hanke, entity)
        saveHankeYhteystiedot(entity, hanke)

        // Creating a new domain object for the return value; it will have the updated values from the database,
        // e.g. the main date values truncated to midnight, and the added id and hanketunnus.
        return createHankeDomainObjectFromEntity(entity)
    }

    override fun updateHanke(hanke: Hanke): Hanke {
        if (hanke.hankeTunnus == null)
            error("Somehow got here with hanke without hanke-tunnus")

        val entity = hankeRepository.findByHankeTunnus(hanke.hankeTunnus!!)
                ?: throw HankeNotFoundException(hanke.hankeTunnus)
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
        // yhteystiedot
        //TODO: maybe we need to check the transaction situation here before the subentity save?
        saveHankeYhteystiedot(entity, hanke)

        // Creating a new domain object for the return value; it will have the updated values from the database,
        // e.g. the main date values truncated to midnight.
        return createHankeDomainObjectFromEntity(entity)
    }

    private fun saveHankeYhteystiedot(entity: HankeEntity, hanke: Hanke) {
        return try {
            logger.info {
                "Saving HankeYhteystietos for ${hanke.hankeTunnus}"
            }

            createEntityFromHankeYhteystiedotDomainObject(hanke, entity)

            entity.listOfHankeYhteystieto?.let { hankeYhteystiedotRepository.saveAll(it) }  //saving hankeyhteystiedot
            logger.info {
                "Saved HankeYhteystieto for ${hanke.hankeTunnus}"
            }
        } catch (e: Exception) {
            logger.error(e) {
                "HankeYhteystiedot save failed"
            }
            throw e
        }
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
                    hankeEntity.suunnitteluVaihe,

                    hankeEntity.version,
                    // TODO: will need in future to actually fetch the username from another service.. (or whatever we choose to pass out here)
                    //   Do it below, outside this construction call.
                    hankeEntity.createdByUserId?.toString() ?: "",
                    // From UTC without timezone info to UTC with timezone info
                    if (hankeEntity.createdAt != null) ZonedDateTime.of(hankeEntity.createdAt, TZ_UTC) else null,
                    hankeEntity.modifiedByUserId?.toString(),
                    if (hankeEntity.modifiedAt != null) ZonedDateTime.of(hankeEntity.modifiedAt, TZ_UTC) else null,

                    hankeEntity.saveType,
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
        internal fun createSeparateYhteystietolistsFromEntityData(hanke: Hanke, hankeEntity: HankeEntity) {

            hankeEntity.listOfHankeYhteystieto?.forEach { hankeYhteysEntity ->
                var hankeYhteystieto = createHankeYhteystietoDomainObjectFromEntity(hankeYhteysEntity)

                if (hankeYhteysEntity.contactType.equals(ContactType.OMISTAJA))
                    hanke.omistajat.add(hankeYhteystieto)
                if (hankeYhteysEntity.contactType.equals(ContactType.TOTEUTTAJA))
                    hanke.toteuttajat.add(hankeYhteystieto)
                if (hankeYhteysEntity.contactType.equals(ContactType.ARVIOIJA))
                    hanke.arvioijat.add(hankeYhteystieto)
            }
        }

        internal fun createHankeYhteystietoDomainObjectFromEntity(hankeYhteystietoEntity: HankeYhteystietoEntity): HankeYhteystieto {
            var createdAt: ZonedDateTime? = null

            if (hankeYhteystietoEntity.createdAt != null)
                createdAt = ZonedDateTime.of(hankeYhteystietoEntity.modifiedAt, TZ_UTC)

            var modifiedAt: ZonedDateTime? = null
            if (hankeYhteystietoEntity.modifiedAt != null)
                modifiedAt = ZonedDateTime.of(hankeYhteystietoEntity.modifiedAt, TZ_UTC)

            return HankeYhteystieto(
                    id = hankeYhteystietoEntity.id,
                    etunimi = hankeYhteystietoEntity.etunimi,
                    sukunimi = hankeYhteystietoEntity.sukunimi,
                    email = hankeYhteystietoEntity.email,
                    puhelinnumero = hankeYhteystietoEntity.puhelinnumero,
                    organisaatioId = hankeYhteystietoEntity.organisaatioid,
                    organisaatioNimi = hankeYhteystietoEntity.organisaationimi,
                    osasto = hankeYhteystietoEntity.osasto,
                    createdBy = hankeYhteystietoEntity.createdByUserId?.toString() ?: "",
                    modifiedBy = hankeYhteystietoEntity.modifiedByUserId?.toString() ?: "",
                    createdAt = createdAt,
                    modifiedAt = modifiedAt
            )
        }
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
        hanke.suunnitteluVaihe?.let { entity.suunnitteluVaihe = hanke.suunnitteluVaihe }

        hanke.saveType?.let { entity.saveType = hanke.saveType }
        hanke.tyomaaKatuosoite?.let { entity.tyomaaKatuosoite = hanke.tyomaaKatuosoite }
        hanke.tyomaaTyyppi.let { entity.tyomaaTyyppi = hanke.tyomaaTyyppi }
        hanke.tyomaaKoko.let { entity.tyomaaKoko = hanke.tyomaaKoko }

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

    ///method combines three lists to one for database
    internal fun createEntityFromHankeYhteystiedotDomainObject(h: Hanke, hankeEntity: HankeEntity) {

        hankeEntity.listOfHankeYhteystieto = mutableListOf<HankeYhteystietoEntity>()

        addHankeYhteystietoEntitysToList(h.omistajat, hankeEntity, ContactType.OMISTAJA)
        addHankeYhteystietoEntitysToList(h.arvioijat, hankeEntity, ContactType.ARVIOIJA)
        addHankeYhteystietoEntitysToList(h.toteuttajat, hankeEntity, ContactType.TOTEUTTAJA)
    }

    internal fun addHankeYhteystietoEntitysToList(listOfHankeYhteystiedot: List<HankeYhteystieto>, hankeEntity: HankeEntity, contactType: ContactType) {

        listOfHankeYhteystiedot.forEach { hankeYht ->

            val hankeYhtEntity = HankeYhteystietoEntity(
                    contactType,
                    hankeYht.sukunimi,
                    hankeYht.etunimi,
                    hankeYht.email,
                    hankeYht.puhelinnumero,
                    hankeYht.organisaatioId,
                    hankeYht.organisaatioNimi,
                    hankeYht.osasto,
                    1, //TODO: real user , make sure you don't update by another user but only once
                    hankeYht.createdAt?.toLocalDateTime(),
                    null, //TODO: real user , make sure updated only if real changes? or what is the design decision?
                    getCurrentTimeUTCAsLocalTime(),  //TODO: only if changed?  do we always want to change the date? how do we know has subobject really been updated?
                    hankeYht.id,
                    hankeEntity
            )
            hankeEntity.listOfHankeYhteystieto!!.add(hankeYhtEntity)
        }

    }

}

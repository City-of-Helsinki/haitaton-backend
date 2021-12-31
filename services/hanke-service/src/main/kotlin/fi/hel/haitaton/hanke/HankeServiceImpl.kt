package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeSearch
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.logging.Action
import fi.hel.haitaton.hanke.logging.PersonalDataAuditLogRepository
import fi.hel.haitaton.hanke.logging.PersonalDataChangeLogRepository
import fi.hel.haitaton.hanke.logging.YhteystietoLoggingEntryHolder
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLaskentaService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulosEntity

import mu.KotlinLogging
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate
import java.time.ZonedDateTime

private val logger = KotlinLogging.logger { }

open class HankeServiceImpl(
    private val hankeRepository: HankeRepository,
    private val tormaystarkasteluService: TormaystarkasteluLaskentaService,
    private val hanketunnusService: HanketunnusService,
    private val personalDataAuditLogRepository: PersonalDataAuditLogRepository,
    private val personalDataChangeLogRepository: PersonalDataChangeLogRepository,
) : HankeService {

    // WITH THIS ONE CAN AUTHORIZE ONLY THE OWNER TO LOAD A HANKE: @PostAuthorize("returnObject.createdBy == authentication.name")
    override fun loadHanke(hankeTunnus: String): Hanke? {
        // TODO: Find out all savetype matches and return the more recent draft vs. submit.
        val entity = hankeRepository.findByHankeTunnus(hankeTunnus)
        if (entity == null)
            return null

        if (entity.id == null) {
            throw DatabaseStateException(hankeTunnus)
        }

        return createHankeDomainObjectFromEntity(entity)
    }

    override fun loadAllHanke(hankeSearch: HankeSearch?): List<Hanke> {

        // TODO: do we limit result by user (e.g. own hanke)?
        // If so, should also apply to the internal loadAll-function variants.

        return if (hankeSearch == null || hankeSearch.isEmpty()) {
            loadAllHanke()
        } else if (hankeSearch.saveType != null) {
            loadAllHankeWithSavetype(hankeSearch.saveType)
        } else {
            //  Get all hanke datas within time period (= either or both of alkuPvm and loppuPvm are inside the requested period)
            loadAllHankeBetweenDates(hankeSearch.periodBegin!!, hankeSearch.periodEnd!!)
        }
    }

    override fun loadHankkeetByIds(ids: List<Int>): List<Hanke> {
        return hankeRepository.findAllById(ids)
            .map { createHankeDomainObjectFromEntity(it) }
    }


    /**
     * Returns all the Hanke items from database for now
     *
     * Returns empty list if no items to return
     * TODO user information to limit what all Hanke items we get?
     */
    internal fun loadAllHanke(): List<Hanke> {
        return hankeRepository.findAll().map { createHankeDomainObjectFromEntity(it) }
    }

    /**
     * Returns all the Hanke items for which the hanke period overlaps with the given period.
     *
     * Returns empty list if no items to return
     * TODO user information to limit what all Hanke items we get?
     */
    internal fun loadAllHankeBetweenDates(periodBegin: LocalDate, periodEnd: LocalDate): List<Hanke> {

        //Hanke ends must be after period start and hanke starts before period ends (that's the reason for parameters going in reversed)
        return hankeRepository.findAllByAlkuPvmIsBeforeAndLoppuPvmIsAfter(periodEnd, periodBegin).map { createHankeDomainObjectFromEntity(it) }
    }

    /**
     * Returns all the Hanke items for which the saveType is the wanted
     *
     * Returns empty list if no items to return
     * TODO user information to limit what all Hanke items we get?
     */
    internal fun loadAllHankeWithSavetype(saveType: SaveType): List<Hanke> {
        return hankeRepository.findAllBySaveType(saveType).map { createHankeDomainObjectFromEntity(it) }
    }

    /**
     * @return a new Hanke instance with the added and possibly modified values.
     */
    override fun createHanke(hanke: Hanke): Hanke {
        // TODO: Only create that hanke-tunnus if a specific set of fields are non-empty/set.

        val userid = SecurityContextHolder.getContext().authentication.name

        val entity = HankeEntity()
        val loggingEntryHolder = prepareLogging(entity)

        // Copy values from the incoming domain object, and set some internal fields:
        copyNonNullHankeFieldsToEntity(hanke, entity)
        val existingYTs = prepareMapOfExistingYhteystietos(entity)
        copyYhteystietosToEntity(hanke, entity, userid, loggingEntryHolder, existingYTs)

        // NOTE: liikennehaittaindeksi and tormaystarkastelutulos are NOT
        //  copied from incoming data. Use setTormaystarkasteluTulos() for that.
        // NOTE: flags are NOT copied from incoming data, as they are set by internal logic.
        // Special fields; handled "manually"..
        entity.version = 0
        entity.createdByUserId = userid
        entity.createdAt = getCurrentTimeUTCAsLocalTime()
        entity.modifiedByUserId = null
        entity.modifiedAt = null

        // Create a new hanketunnus for it and save it:
        val hanketunnus = hanketunnusService.newHanketunnus()
        entity.hankeTunnus = hanketunnus
        logger.debug {
            "Creating Hanke ${hanke.hankeTunnus}: ${hanke.toLogString()}"
        }
        val savedHankeEntity = hankeRepository.save(entity)
        logger.debug {
            "Created Hanke ${hanke.hankeTunnus}."
        }

        postProcessAndSaveLogging(loggingEntryHolder, savedHankeEntity, userid)

        // Creating a new domain object for the return value; it will have the updated values from the database,
        // e.g. the main date values truncated to midnight, and the added id and hanketunnus.
        return createHankeDomainObjectFromEntity(entity)
    }

    // WITH THIS ONE CAN AUTHORIZE ONLY THE OWNER TO UPDATE A HANKE: @PreAuthorize("#hanke.createdBy == authentication.name")
    override fun updateHanke(hanke: Hanke): Hanke {
        if (hanke.hankeTunnus == null)
            error("Somehow got here with hanke without hanke-tunnus")

        val userid = SecurityContextHolder.getContext().authentication.name

        // Both checks that the hanke already exists, and get its old fields to transfer data into
        val entity = hankeRepository.findByHankeTunnus(hanke.hankeTunnus!!)
                ?: throw HankeNotFoundException(hanke.hankeTunnus!!)

        val existingYTs = prepareMapOfExistingYhteystietos(entity)
        checkAndHandleDataProcessingRestrictions(hanke, entity, existingYTs, userid)

        val loggingEntryHolder = prepareLogging(entity)
        // Transfer field values from domain object to entity object, and set relevant audit fields:
        copyNonNullHankeFieldsToEntity(hanke, entity)
        copyYhteystietosToEntity(hanke, entity, userid, loggingEntryHolder, existingYTs)

        // NOTE: flags are NOT copied from incoming data, as they are set by internal logic.
        // Special fields; handled "manually"..
        entity.version = entity.version?.inc() ?: 1
        // (Not changing createdBy/At fields.)
        entity.modifiedByUserId = userid
        entity.modifiedAt = getCurrentTimeUTCAsLocalTime()

        tormaystarkasteluService.calculateTormaystarkastelu(hanke)?.let { tulos ->
            val tulosEntity = copyTormaystarkasteluTulosToEntity(tulos)
            copyTormaystarkasteluTulosToHankeEntity(tulosEntity, entity)
        }

        // Save changes:
        logger.debug {
            "Saving Hanke ${hanke.hankeTunnus}: ${hanke.toLogString()}"
        }
        val savedHankeEntity = hankeRepository.save(entity)
        logger.debug {
            "Saved Hanke ${hanke.hankeTunnus}."
        }

        postProcessAndSaveLogging(loggingEntryHolder, savedHankeEntity, userid)

        // Creating a new domain object for the return value; it will have the updated values from the database,
        // e.g. the main date values truncated to midnight.
        return createHankeDomainObjectFromEntity(entity)
    }

    fun updateHankeStateFlags(hanke: Hanke) {
        if (hanke.hankeTunnus == null) {
            error("Somehow got here with hanke without hanke-tunnus")
        }
        // Both checks that the hanke already exists, and get its old fields to transfer data into
        val entity = hankeRepository.findByHankeTunnus(hanke.hankeTunnus!!)
                ?: throw HankeNotFoundException(hanke.hankeTunnus!!)
        copyStateFlagsToEntity(hanke, entity)
        hankeRepository.save(entity)
    }

    // TODO: functions to remove and invalidate Hanke's tormaystarkastelu-data
    //   At least invalidation can be done purely working on the particular
    //   tormaystarkasteluTulosEntity and -Repository.
    //   See TormaystarkasteluRepositoryITests for a way to remove.

    // ======================================================================

    // --------------- Helpers for data transfer from database ------------

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
                    hankeEntity.createdByUserId ?: "",
                    // From UTC without timezone info to UTC with timezone info
                    if (hankeEntity.createdAt != null) ZonedDateTime.of(hankeEntity.createdAt, TZ_UTC) else null,
                    hankeEntity.modifiedByUserId,
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

            h.liikennehaittaindeksi = hankeEntity.liikennehaittaIndeksi?.copy()

            if (!hankeEntity.tormaystarkasteluTulokset.isEmpty()) {
                val tttE = hankeEntity.tormaystarkasteluTulokset.get(0)
                val ttt = TormaystarkasteluTulos(hankeEntity.hankeTunnus!!)
                ttt.hankeId = h.id!!
                ttt.liikennehaittaIndeksi = tttE.liikennehaitta?.copy()
                ttt.perusIndeksi = tttE.perus
                ttt.joukkoliikenneIndeksi = tttE.joukkoliikenne
                ttt.pyorailyIndeksi = tttE.pyoraily
                ttt.tila = tttE.tila
                h.tormaystarkasteluTulos = ttt
            }

            copyStateFlagsFromEntity(h, hankeEntity)
            h.updateStateFlags()

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
                    createdBy = hankeYhteystietoEntity.createdByUserId,
                    modifiedBy = hankeYhteystietoEntity.modifiedByUserId,
                    createdAt = createdAt,
                    modifiedAt = modifiedAt
            )
        }

        private fun copyStateFlagsFromEntity(h: Hanke, entity: HankeEntity) {
            h.tilat.onViereisiaHankkeita = entity.tilaOnViereisiaHankkeita
            h.tilat.onAsiakasryhmia = entity.tilaOnAsiakasryhmia
        }

    }

    // --------------- Helpers for data transfer towards database ------------

    /**
     * Checks if some to be changed or deleted yhteystieto has "datalocked" field set
     * in the currently persisted entry.
     * If so, creates audit- and changelog-entries and throws an exception.
     * The exception prevents any other changes to the Hanke or other yhteystietos
     * to be saved. (The restricted yhteystietos would need to be left as is in order
     * to make any other changes to Hanke data). (NOTE: the current implementation may
     * have insufficient feedback in the UI about the situation.)
     *
     * The datalocked field can be used e.g. to handle GDPR "personal data processing
     * restriction" requirement. It can be used for other "prevent changes" purposes,
     * too, but current implementation has been done with GDPR mostly in mind, so
     * e.g. some messages/comments/log entry info could be misleading for other uses.
     */
    private fun checkAndHandleDataProcessingRestrictions(
            incomingHanke: Hanke,
            persistedEntity: HankeEntity,
            existingYTs: MutableMap<Int, HankeYhteystietoEntity>,
            userid: String
    ) {
        logger.debug {
            "Checking for processing restrictions. Have ${existingYTs.size} existing Yhteystietos."
        }
        // Are there any locked yhteystietos? Very Likely not, so make that easy check first:
        val lockedExistingYTs: MutableMap<Int, HankeYhteystietoEntity> = mutableMapOf()
        existingYTs.values.forEach {
            if (it.dataLocked == true) {
                lockedExistingYTs[it.id!!] = it
            }
        }
        // If there are no existing locked yhteystietos, can proceed with the update:
        if (lockedExistingYTs.isEmpty()) {
            logger.debug {
                "Yhteystietos of the Hanke have no processing restrictions. Hanke update can continue."
            }
            return
        }

        logger.debug {
            "${lockedExistingYTs.size} of the existing Yhteystietos have processing restrictions. Checking for effects."
        }
        val loggingEntryHolderForRestrictedActions = prepareLogging(persistedEntity)
        // Some is locked, check incoming data and compare, looking for changes or deletions.
        // A temporary copy, so that we can remove handled entries from it, leaving
        // certain deleted entries as remainder:
        val tempLockedExistingYts = lockedExistingYTs.toMutableMap()
        findAndLogAffectedBlockedYhteystietos(
            incomingHanke.omistajat, tempLockedExistingYts, loggingEntryHolderForRestrictedActions, userid)
        findAndLogAffectedBlockedYhteystietos(
            incomingHanke.arvioijat, tempLockedExistingYts, loggingEntryHolderForRestrictedActions, userid)
        findAndLogAffectedBlockedYhteystietos(
            incomingHanke.toteuttajat, tempLockedExistingYts, loggingEntryHolderForRestrictedActions, userid)

        // If no entries were blocked, and there is nothing left in the tempLockedExistingYts, all clear to proceed:
        if (!loggingEntryHolderForRestrictedActions.hasEntries() && tempLockedExistingYts.isEmpty()) {
            logger.debug {
                "No actual changes to the restricted Yhteystietos. Hanke update can continue."
            }
            return
        }

        // Any remaining entries in the tempLockedExistingYts means those would be deleted, if not locked;
        // create audit log entries for them:
        tempLockedExistingYts.values.forEach {
            loggingEntryHolderForRestrictedActions.addLogEntriesForEvent(
                Action.DELETE, true,
                "delete hanke yhteystieto BLOCKED by data processing restriction",
                it, null, userid)
        }
        logger.warn { "Hanke update with actions on processing restricted data, saving details to audit and change logs. Hanke id ${incomingHanke.id} will be left unchanged" }
        // This will throw exception (after saving the entries) and prevents
        // the Hanke update from continuing to actual changes to it.
        postProcessAndSaveLoggingForRestrictions(loggingEntryHolderForRestrictedActions)
    }

    private fun findAndLogAffectedBlockedYhteystietos(
            incomingYts: MutableList<HankeYhteystieto>,
            tempLockedExistingYts: MutableMap<Int, HankeYhteystietoEntity>,
            loggingEntryHolderForRestrictedActions: YhteystietoLoggingEntryHolder,
            userid: String
    ) {
        incomingYts.forEach { yhteystieto ->
            val lockedExistingYt = tempLockedExistingYts[yhteystieto.id]
            if (lockedExistingYt != null) {
                // If all main fields are empty, consider it as deletion;
                // otherwise check if any of the fields have changes compared to existing values.
                if (!yhteystieto.isAnyFieldSet()) {
                    loggingEntryHolderForRestrictedActions.addLogEntriesForEvent(
                        Action.DELETE, true,
                        "delete hanke yhteystieto BLOCKED by data processing restriction",
                        tempLockedExistingYts[yhteystieto.id], null, userid)
                } else if (!areEqualIncomingVsExistingYhteystietos(yhteystieto, lockedExistingYt)) {
                    // copy the values to temporary non-persisted entity for logging:
                    val unsavedNewData = lockedExistingYt.cloneWithMainFields()
                    unsavedNewData.sukunimi = yhteystieto.sukunimi
                    unsavedNewData.etunimi = yhteystieto.etunimi
                    unsavedNewData.email = yhteystieto.email
                    unsavedNewData.puhelinnumero = yhteystieto.puhelinnumero
                    unsavedNewData.organisaatioId = yhteystieto.organisaatioId
                    yhteystieto.organisaatioNimi?.let { unsavedNewData.organisaatioNimi = it }
                    yhteystieto.osasto?.let { unsavedNewData.osasto = it }
                    loggingEntryHolderForRestrictedActions.addLogEntriesForEvent(
                        Action.UPDATE, true,
                        "update hanke yhteystieto BLOCKED by data processing restriction",
                        lockedExistingYt, unsavedNewData, userid)
                }
                tempLockedExistingYts.remove(yhteystieto.id!!)
            }
        }
    }

    private fun copyStateFlagsToEntity(hanke: Hanke, entity: HankeEntity) {
        entity.tilaOnViereisiaHankkeita = hanke.tilat.onViereisiaHankkeita
        entity.tilaOnAsiakasryhmia = hanke.tilat.onAsiakasryhmia
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
     * Creates a temporary id-to-existingyhteystieto -map. It can be used to find
     * quickly whether an incoming Yhteystieto is new or already in the database.
     * (Also, copyYhteystietosToEntity() and its subfunctions remove entries from such map
     * as they process them, and any remaining entries in it are considered as to
     * be removed.)
     * The given hankeEntity and its yhteystietos MUST be in persisted state (have their id's set).
     */
    private fun prepareMapOfExistingYhteystietos(hankeEntity: HankeEntity): MutableMap<Int, HankeYhteystietoEntity> {
        // Create temporary id-to-existingyhteystieto -map, used to find quickly whether an incoming Yhteystieto is new or exists already.
        // YT = Yhteystieto
        val existingYTs: MutableMap<Int, HankeYhteystietoEntity> = mutableMapOf()
        for (existingYT in hankeEntity.listOfHankeYhteystieto) {
            val ytid = existingYT.id
            if (ytid == null) {
                throw DatabaseStateException("A persisted HankeYhteystietoEntity somehow missing id, Hanke id ${hankeEntity.id}")
            } else {
                existingYTs[ytid] = existingYT
            }
        }
        return existingYTs
    }

    /**
     * Transfers yhteystieto fields from domain to (new or existing) entity object,
     * combines the three lists into one list, and sets the audit fields as relevant.
     */
    private fun copyYhteystietosToEntity(
        hanke: Hanke, entity: HankeEntity, userid: String,
        loggingEntryHolder: YhteystietoLoggingEntryHolder,
        existingYTs: MutableMap<Int, HankeYhteystietoEntity>
    ) {
        // Note, if the incoming data indicates it is an already saved yhteystieto (id-field is set), should try
        // to transfer the business fields to the same old corresponding entity. Pretty much a must in order to
        // preserve createdBy and createdAt field values without having to rely on the client-side to hold
        // the values for us (bad design), which would also require checks on those (to prevent tampering).

        // Check each incoming yhteystieto (from three lists) for being new or an update to existing one,
        // and add to the main entity's single list if necessary:
        processIncomingHankeYhteystietosOfSpecificTypeToEntity(hanke.omistajat, entity, ContactType.OMISTAJA, userid, existingYTs, loggingEntryHolder)
        processIncomingHankeYhteystietosOfSpecificTypeToEntity(hanke.arvioijat, entity, ContactType.ARVIOIJA, userid, existingYTs, loggingEntryHolder)
        processIncomingHankeYhteystietosOfSpecificTypeToEntity(hanke.toteuttajat, entity, ContactType.TOTEUTTAJA, userid, existingYTs, loggingEntryHolder)

        // TODO: this method of removing entries if they are missing in the incoming data is different to
        //     behavior of the other simpler fields, where missing or null field is considered "keep the existing value,
        //     and return it back in response".
        //     However, those simpler fields can not be removed as a whole, so they _can_ behave so.
        //     For clarity, yhteystieto-entries should have separate action for removal (but then they should also
        //     have separate action for addition, i.e. own API endpoint in controller).
        //     So, consider the code below as "for now".
        // TODO: Yhteystietos should not be in a list, but a "bag", or ensure it is e.g. linkedlist instead of arraylist (or similars).
        //    The order of Yhteystietos does not matter(?), and removing things from e.g. array list
        //    gets inefficient. Since there are so few entries, this crude solution works, for now.
        // If there is anything left in the existingYTs map, they have been removed in the incoming data,
        // so remove them from the entity's list and make the back-reference null (and thus delete from the database).
        for (hankeYht in existingYTs.values) {
            entity.removeYhteystieto(hankeYht)
            loggingEntryHolder.addLogEntriesForEvent(
                Action.DELETE, false, "delete hanke yhteystieto", hankeYht, null, userid)
        }
    }


    private fun processIncomingHankeYhteystietosOfSpecificTypeToEntity(
        listOfHankeYhteystiedot: List<HankeYhteystieto>,
        hankeEntity: HankeEntity,
        contactType: ContactType,
        userid: String,
        existingYTs: MutableMap<Int, HankeYhteystietoEntity>,
        loggingEntryHolder: YhteystietoLoggingEntryHolder
    ) {
        for (hankeYht in listOfHankeYhteystiedot) {
            val someFieldsSet = hankeYht.isAnyFieldSet()
            var validYhteystieto = false
            // TODO: yhteystietovalidation is as of now a mess, due to multiple reasons.
            //   Just rethink it all everywhere before trying to implement something..
            // The UI has currently bigger problems implementing it so that Yhteystieto entries that haven't even been touched
            //   would not be sent to backend as a group of ""-fields, so the condition here is to make
            //   such fully-empty entry considered as non-existent (i.e. skip it).
            // If any field is given (not empty and not only whitespace)...
            if (someFieldsSet) {
                validYhteystieto = hankeYht.isAnyFieldSet()
                // Check if at least the 4 mandatory fields are given
                //validYhteystieto = hankeYht.isValid()
            }

            // Is the incoming Yhteystieto new (does not have id, create new) or old (has id, update existing)?
            if (hankeYht.id == null) {
                // New Yhteystieto
                // Note: don't need to (and can not) create audit-log entries during this create processing;
                // they are done later, after the whole hanke has been saved and new yhteystietos got their db-ids.
                processCreateYhteystieto(hankeYht, validYhteystieto, contactType, userid, hankeEntity)
            } else {
                // Should be an existing Yhteystieto
                processUpdateYhteystieto(hankeYht, existingYTs, someFieldsSet, validYhteystieto, userid, hankeEntity, loggingEntryHolder)
            }
        }
    }

    private fun processCreateYhteystieto(
        hankeYht: HankeYhteystieto,
        validYhteystieto: Boolean,
        contactType: ContactType,
        userid: String,
        hankeEntity: HankeEntity
    ) {
        if (validYhteystieto) {
            // ... it is valid, so create a new Yhteystieto
            val hankeYhtEntity = HankeYhteystietoEntity(
                    contactType,
                    hankeYht.sukunimi,
                    hankeYht.etunimi,
                    hankeYht.email,
                    hankeYht.puhelinnumero,
                    hankeYht.organisaatioId,
                    hankeYht.organisaatioNimi,
                    hankeYht.osasto,

                    false,
                    null,

                    userid, // createdByUserId
                    getCurrentTimeUTCAsLocalTime(), // createdAt
                    null,
                    null,
                    null, // will be set by the database
                    hankeEntity) // reference back to parent hanke
            hankeEntity.addYhteystieto(hankeYhtEntity)
            // Logging of creating new yhteystietos is done after the hanke gets saved.
        } else {
            // ... missing some mandatory fields, should not have gotten here. Log it and skip it.
            logger.error {
                "Got a new Yhteystieto object with one or more empty mandatory fields, skipping it. HankeId ${hankeEntity.id}"
            }
        }
    }

    private fun processUpdateYhteystieto(
        hankeYht: HankeYhteystieto,
        existingYTs: MutableMap<Int, HankeYhteystietoEntity>,
        someFieldsSet: Boolean,
        validYhteystieto: Boolean,
        userid: String,
        hankeEntity: HankeEntity,
        loggingEntryHolder: YhteystietoLoggingEntryHolder
    ) {
        // If incoming Yhteystieto has id set, it _should_ be among the existing Yhteystietos, or some kind of error has happened.
        val incomingId: Int = hankeYht.id!!
        val existingYT: HankeYhteystietoEntity? = existingYTs[incomingId]
        if (existingYT == null) {
            // Some sort of error situation;
            // - simultaneous edits to the same hanke by someone else (the Yhteystieto could have been removed in the database)
            // - the incoming ids are for different hanke (i.e. incorrect data in the incoming request)
            throw HankeYhteystietoNotFoundException(hankeEntity.id!!, incomingId)
        }

        if (validYhteystieto) {
            // Check if anything actually changed (UI can send all data as is on every request)
            if (!areEqualIncomingVsExistingYhteystietos(hankeYht, existingYT)) {
                // Record old data as JSON for change logging (and id for other purposes)
                val previousEntityData = existingYT.cloneWithMainFields()
                previousEntityData.id = existingYT.id

                // Update values in the persisted entity:
                existingYT.sukunimi = hankeYht.sukunimi
                existingYT.etunimi = hankeYht.etunimi
                existingYT.email = hankeYht.email
                existingYT.puhelinnumero = hankeYht.puhelinnumero
                existingYT.organisaatioId = hankeYht.organisaatioId
                hankeYht.organisaatioNimi?.let { existingYT.organisaatioNimi = hankeYht.organisaatioNimi }
                hankeYht.osasto?.let { existingYT.osasto = hankeYht.osasto }

                // (Not changing createdBy/At fields)
                existingYT.modifiedByUserId = userid
                existingYT.modifiedAt = getCurrentTimeUTCAsLocalTime()
                // (Not touching the id or hanke fields)

                loggingEntryHolder.addLogEntriesForEvent(
                    Action.UPDATE, false, "update hanke yhteystieto", previousEntityData, existingYT, userid)
            }

            // No need to add the existing Yhteystieto entity to the hanke's list; it is already in it.
            // Remove the corresponding entry from the map. (Afterwards, the entries remaining in the map
            // were not in the incoming data, so should be removed from the database.)
            existingYTs.remove(incomingId)
        } else {
            // Trying to update an Yhteystieto with one or more empty mandatory data fields.
            // If we do not change anything, the response will send back to previous stored values.
            // However, checking one special case; all fields being empty. This corresponds to initial state,
            // where the corresponding Yhteystieto is not set. Therefore, for now, considering it as "please delete".
            // (Handling it by reversed logic using the existingYTs map, see the comment above in the do-update -case.)
            if (someFieldsSet) {
                logger.error {
                    "Got a new Yhteystieto object with one or more empty mandatory fields, skipping it. HankeId ${hankeEntity.id}"
                }
                existingYTs.remove(incomingId)
            }
            // If the entry was left in the existingYTs, it will get deleted.
        }
    }

    private fun areEqualIncomingVsExistingYhteystietos(incoming: HankeYhteystieto, existing: HankeYhteystietoEntity): Boolean {
        if (incoming.etunimi != existing.etunimi) return false
        if (incoming.sukunimi != existing.sukunimi) return false
        if (incoming.email != existing.email) return false
        if (incoming.puhelinnumero != existing.puhelinnumero) return false
        if (incoming.organisaatioId != existing.organisaatioId) return false
        if (incoming.organisaatioNimi != existing.organisaatioNimi) return false
        if (incoming.osasto != existing.osasto) return false
        return true
    }

    private fun copyTormaystarkasteluTulosToEntity(ttt: TormaystarkasteluTulos): TormaystarkasteluTulosEntity {
        val tttEntity = TormaystarkasteluTulosEntity()
        tttEntity.liikennehaitta = ttt.liikennehaittaIndeksi?.copy()
        tttEntity.perus = ttt.perusIndeksi
        tttEntity.pyoraily = ttt.pyorailyIndeksi
        tttEntity.joukkoliikenne = ttt.joukkoliikenneIndeksi
        tttEntity.tila = ttt.tila
        tttEntity.createdAt = getCurrentTimeUTCAsLocalTime()
        return tttEntity
    }

    private fun copyTormaystarkasteluTulosToHankeEntity(ttte: TormaystarkasteluTulosEntity, hankeEntity: HankeEntity) {
        hankeEntity.liikennehaittaIndeksi = ttte.liikennehaitta?.copy()
        hankeEntity.tormaystarkasteluTulokset.clear()
        hankeEntity.addTormaystarkasteluTulos(ttte)
    }

    /**
     * Creates the entry holder object and initializes the old Yhteystieto id set
     * (so that it can know later which yhteystietos are created during the ongoing request).
     */
    private fun prepareLogging(entity: HankeEntity): YhteystietoLoggingEntryHolder {
        val loggingEntryHolder = YhteystietoLoggingEntryHolder()
        loggingEntryHolder.initWithOldYhteystietos(entity.listOfHankeYhteystieto)
        return loggingEntryHolder
    }

    /**
     * Handles post-processing of logging entries about restricted actions.
     * Applies request's IP to all given logging entries, saves them, and creates
     * and throws an exception which indicates that restricted yhteystietos
     * can not be changed/deleted.
     */
    private fun postProcessAndSaveLoggingForRestrictions(loggingEntryHolderForRestrictedActions: YhteystietoLoggingEntryHolder) {
        loggingEntryHolderForRestrictedActions.applyIPaddresses()
        loggingEntryHolderForRestrictedActions.saveLogEntries(personalDataAuditLogRepository, personalDataChangeLogRepository)
        val idList = StringBuilder()
        loggingEntryHolderForRestrictedActions.auditLogEntries.forEach { idList.append(it.id).append(", ") }
        if (idList.endsWith(", "))
            idList.setLength(idList.length-2)
        throw HankeYhteystietoProcessingRestrictedException("Can not modify/delete yhteystieto which has data processing restricted (id: $idList)")
    }

    /**
     * Handles logging of all newly created Yhteystietos, applies request's IP to all log entries,
     * and saves all the log entries.
     * Do not use this for the "restricted action" log events; see
     * postProcessAndSaveLoggingForRestrictions() for that.
     */
    private fun postProcessAndSaveLogging(
        loggingEntryHolder: YhteystietoLoggingEntryHolder,
        savedHankeEntity: HankeEntity,
        userid: String
    ) {
        // It would be possible to process all action types the same way afterwards
        // like for creating new yhteystietos, but that would cause some (relatively)
        // minor extra work, and, the proper solution would be to handle personal data
        // access in its own separate service and do the logging there independently...
        // So, the current way of doing things should be good enough for now.
        loggingEntryHolder.addLogEntriesForNewYhteystietos(savedHankeEntity.listOfHankeYhteystieto, userid)
        loggingEntryHolder.applyIPaddresses()
        loggingEntryHolder.saveLogEntries(personalDataAuditLogRepository, personalDataChangeLogRepository)
    }
}

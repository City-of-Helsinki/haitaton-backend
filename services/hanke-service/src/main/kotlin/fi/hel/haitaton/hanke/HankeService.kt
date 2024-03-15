package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.CableReportWithoutHanke
import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentService
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankePerustaja
import fi.hel.haitaton.hanke.domain.HankeRequest
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.domain.ModifyHankeRequest
import fi.hel.haitaton.hanke.domain.ModifyHankeYhteystietoRequest
import fi.hel.haitaton.hanke.domain.Yhteystieto
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.logging.HankeLoggingService
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.YhteystietoLoggingEntryHolder
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.validation.HankePublicValidator
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.context.SecurityContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class HankeService(
    private val hankeRepository: HankeRepository,
    private val hanketunnusService: HanketunnusService,
    private val hankealueService: HankealueService,
    private val hankeLoggingService: HankeLoggingService,
    private val applicationService: ApplicationService,
    private val hakemusService: HakemusService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val hankeAttachmentService: HankeAttachmentService,
) {

    @Transactional(readOnly = true)
    fun findIdentifier(hankeTunnus: String): HankeIdentifier? =
        hankeRepository.findOneByHankeTunnus(hankeTunnus)

    @Transactional(readOnly = true)
    fun findIdentifier(hankeId: Int): HankeIdentifier? = hankeRepository.findOneById(hankeId)

    @Transactional(readOnly = true)
    fun getHankeApplications(hankeTunnus: String): List<Application> =
        hankeRepository.findByHankeTunnus(hankeTunnus)?.let { entity ->
            entity.hakemukset.map { hakemus -> hakemus.toApplication() }
        } ?: throw HankeNotFoundException(hankeTunnus)

    @Transactional(readOnly = true)
    fun loadHanke(hankeTunnus: String): Hanke? =
        hankeRepository.findByHankeTunnus(hankeTunnus)?.let {
            createHankeDomainObjectFromEntity(it)
        }

    @Transactional(readOnly = true)
    fun loadPublicHanke(): List<Hanke> =
        hankeRepository.findAllByStatus(HankeStatus.PUBLIC).map {
            createHankeDomainObjectFromEntity(it)
        }

    @Transactional(readOnly = true)
    fun loadHankeById(id: Int): Hanke? =
        hankeRepository.findByIdOrNull(id)?.let { createHankeDomainObjectFromEntity(it) }

    @Transactional(readOnly = true)
    fun loadHankkeetByIds(ids: List<Int>) =
        hankeRepository.findAllById(ids).map { createHankeDomainObjectFromEntity(it) }

    @Transactional
    fun createHanke(
        request: CreateHankeRequest,
        securityContext: SecurityContext,
    ): Hanke {
        val entity = createNewEntity(request.nimi, false, securityContext.userId())

        return saveCreatedHanke(entity, request.perustaja, securityContext)
    }

    /**
     * Create application when no existing hanke. Autogenerates hanke and applies application to it.
     */
    @Transactional
    fun generateHankeWithApplication(
        cableReport: CableReportWithoutHanke,
        securityContext: SecurityContext,
    ): Application {
        logger.info { "Generating Hanke from CableReport." }
        val hanke = generateHankeFrom(cableReport, securityContext)
        return applicationService.create(
            cableReport.toNewApplication(hanke.hankeTunnus),
            securityContext.userId()
        )
    }

    /**
     * Create application when no existing hanke. Autogenerates hanke and applies application to it.
     */
    @Transactional
    fun generateHankeWithJohtoselvityshakemus(
        request: CreateHankeRequest,
        securityContext: SecurityContext,
    ): Hakemus {
        logger.info { "Creating a Hanke for a stand-alone cable report." }
        val hanke = createNewEntity(limitHankeName(request.nimi), true, securityContext.userId())
        saveCreatedHanke(hanke, request.perustaja, securityContext)
        logger.info { "Creating the stand-alone johtoselvityshakemus." }
        return hakemusService.createJohtoselvitys(hanke, securityContext.userId())
    }

    @Transactional
    fun updateHanke(hankeTunnus: String, hanke: ModifyHankeRequest): Hanke {
        val userId = currentUserId()

        // Both checks that the hanke already exists, and get its old fields to transfer data into
        val entity =
            hankeRepository.findByHankeTunnus(hankeTunnus)
                ?: throw HankeNotFoundException(hankeTunnus)

        val hankeBeforeUpdate = createHankeDomainObjectFromEntity(entity)

        val existingYTs = prepareMapOfExistingYhteystietos(entity)
        checkAndHandleDataProcessingRestrictions(hanke, entity, existingYTs, userId)

        val loggingEntryHolder = prepareLogging(entity)
        // Transfer field values from request object to entity object
        updateEntityFieldsFromRequest(hanke, entity)
        copyYhteystietosToEntity(hanke, entity, userId, loggingEntryHolder, existingYTs)

        // Set relevant audit fields:
        // NOTE: flags are NOT copied from incoming data, as they are set by internal logic.
        // Special fields; handled "manually"..
        entity.version = entity.version?.inc() ?: 1
        // (Not changing createdBy/At fields.)
        entity.modifiedByUserId = userId
        entity.modifiedAt = getCurrentTimeUTCAsLocalTime()
        entity.generated = false

        calculateTormaystarkastelu(hanke.alueet, entity.alueet.geometriaIds(), entity)
        entity.status = decideNewHankeStatus(entity)

        logger.debug { "Saving Hanke ${entity.logString()}." }
        val savedHankeEntity = hankeRepository.save(entity)
        logger.debug { "Saved Hanke ${entity.logString()}." }

        postProcessAndSaveLogging(loggingEntryHolder, savedHankeEntity, userId)

        return createHankeDomainObjectFromEntity(entity).also {
            hankeLoggingService.logUpdate(hankeBeforeUpdate, it, userId)
        }
    }

    @Transactional
    fun deleteHanke(hankeTunnus: String, userId: String) {
        val hanke = loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)
        val hakemukset = getHankeApplications(hankeTunnus)

        if (anyHakemusProcessingInAllu(hakemukset)) {
            throw HankeAlluConflictException(
                "Hanke ${hanke.hankeTunnus} has hakemus in Allu processing. Cannot delete."
            )
        }

        hakemukset.forEach { hakemus ->
            hakemus.id?.let { id -> applicationService.delete(id, userId) }
        }

        hankeAttachmentService.deleteAllAttachments(hanke)

        hankeRepository.deleteById(hanke.id)
        hankeLoggingService.logDelete(hanke, userId)
    }

    private fun anyHakemusProcessingInAllu(hakemukset: List<Application>): Boolean =
        hakemukset.any {
            logger.info { "Hakemus ${it.id} has alluStatus ${it.alluStatus}" }
            !applicationService.isStillPending(it.alluid, it.alluStatus)
        }

    private fun calculateTormaystarkastelu(
        alueet: List<Hankealue>,
        geometriaIds: Set<Int>,
        target: HankeEntity
    ) {
        hankealueService.calculateTormaystarkastelu(alueet, geometriaIds, target)?.let {
            target.tormaystarkasteluTulokset.clear()
            target.tormaystarkasteluTulokset.add(it)
        }
    }

    private fun decideNewHankeStatus(entity: HankeEntity): HankeStatus {
        val validationResult =
            HankePublicValidator.validateHankeHasMandatoryFields(
                createHankeDomainObjectFromEntity(entity)
            )

        return when (val status = entity.status) {
            HankeStatus.DRAFT ->
                if (validationResult.isOk()) {
                    HankeStatus.PUBLIC
                } else {
                    logger.debug {
                        "A hanke draft wasn't ready to go public. hankeTunnus=${entity.hankeTunnus} failedFields=${validationResult.errorPaths().joinToString()}"
                    }
                    HankeStatus.DRAFT
                }
            HankeStatus.PUBLIC ->
                if (validationResult.isOk()) {
                    HankeStatus.PUBLIC
                } else {
                    logger.warn {
                        "A public hanke wasn't updated with missing or invalid fields. hankeTunnus=${entity.hankeTunnus} failedFields=${validationResult.errorPaths().joinToString()}"
                    }
                    throw HankeArgumentException(
                        "A public hanke didn't have all mandatory fields filled."
                    )
                }
            else ->
                throw HankeArgumentException(
                    "A hanke cannot be updated when in status $status. hankeTunnus=${entity.hankeTunnus}"
                )
        }
    }

    private fun createHankeDomainObjectFromEntity(hankeEntity: HankeEntity): Hanke =
        HankeMapper.domainFrom(hankeEntity, hankealueService.geometryMapFrom(hankeEntity.alueet))

    // --------------- Helpers for data transfer towards database ------------

    /**
     * Checks if some to be changed or deleted yhteystieto has "datalocked" field set in the
     * currently persisted entry. If so, creates audit- and changelog-entries and throws an
     * exception. The exception prevents any other changes to the Hanke or other yhteystietos to be
     * saved. (The restricted yhteystietos would need to be left as is in order to make any other
     * changes to Hanke data). (NOTE: the current implementation may have insufficient feedback in
     * the UI about the situation.)
     *
     * The dataLocked field can be used e.g. to handle GDPR "personal data processing restriction"
     * requirement. It can be used for other "prevent changes" purposes, too, but current
     * implementation has been done with GDPR mostly in mind, so e.g. some messages/comments/log
     * entry info could be misleading for other uses.
     */
    private fun checkAndHandleDataProcessingRestrictions(
        incomingHanke: HankeRequest,
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
            incomingHanke.extractYhteystiedot(),
            tempLockedExistingYts,
            loggingEntryHolderForRestrictedActions,
            userid
        )

        // If no entries were blocked, and there is nothing left in the tempLockedExistingYts, all
        // clear to proceed:
        if (
            !loggingEntryHolderForRestrictedActions.hasEntries() && tempLockedExistingYts.isEmpty()
        ) {
            logger.debug {
                "No actual changes to the restricted Yhteystietos. Hanke update can continue."
            }
            return
        }

        // Any remaining entries in the tempLockedExistingYts means those would be deleted, if not
        // locked;
        // create audit log entries for them:
        tempLockedExistingYts.values.forEach {
            loggingEntryHolderForRestrictedActions.addLogEntriesForEvent(
                operation = Operation.DELETE,
                failed = true,
                failureDescription =
                    "delete hanke yhteystieto BLOCKED by data processing restriction",
                oldEntity = it,
                newEntity = null,
                userId = userid,
            )
        }
        logger.warn {
            "Hanke update with actions on processing restricted data, " +
                "saving details to audit and change logs. " +
                "Hanke will be left unchanged. ${persistedEntity.logString()}"
        }
        // This will throw exception (after saving the entries) and prevents
        // the Hanke update from continuing to actual changes to it.
        postProcessAndSaveLoggingForRestrictions(loggingEntryHolderForRestrictedActions)
    }

    private fun findAndLogAffectedBlockedYhteystietos(
        incomingYts: List<Yhteystieto>,
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
                        operation = Operation.DELETE,
                        failed = true,
                        failureDescription =
                            "delete hanke yhteystieto BLOCKED by data processing restriction",
                        oldEntity = tempLockedExistingYts[yhteystieto.id],
                        newEntity = null,
                        userId = userid,
                    )
                } else if (!areEqualIncomingVsExistingYhteystietos(yhteystieto, lockedExistingYt)) {
                    // copy the values to temporary non-persisted entity for logging:
                    val unsavedNewData = lockedExistingYt.cloneWithMainFields()
                    unsavedNewData.nimi = yhteystieto.nimi
                    unsavedNewData.email = yhteystieto.email
                    unsavedNewData.puhelinnumero = yhteystieto.puhelinnumero
                    yhteystieto.organisaatioNimi?.let { unsavedNewData.organisaatioNimi = it }
                    yhteystieto.osasto?.let { unsavedNewData.osasto = it }
                    loggingEntryHolderForRestrictedActions.addLogEntriesForEvent(
                        operation = Operation.UPDATE,
                        failed = true,
                        failureDescription =
                            "update hanke yhteystieto BLOCKED by data processing restriction",
                        oldEntity = lockedExistingYt,
                        newEntity = unsavedNewData,
                        userId = userid,
                    )
                }
                tempLockedExistingYts.remove(yhteystieto.id!!)
            }
        }
    }

    private fun updateEntityFieldsFromRequest(hanke: ModifyHankeRequest, entity: HankeEntity) {
        entity.onYKTHanke = hanke.onYKTHanke
        entity.nimi = hanke.nimi
        entity.kuvaus = hanke.kuvaus
        entity.vaihe = hanke.vaihe
        entity.tyomaaKatuosoite = hanke.tyomaaKatuosoite
        val newTyyppi = hanke.tyomaaTyyppi.minus(entity.tyomaaTyyppi)
        val removedTyyppi = entity.tyomaaTyyppi.minus(hanke.tyomaaTyyppi)
        entity.tyomaaTyyppi.removeAll(removedTyyppi)
        entity.tyomaaTyyppi.addAll(newTyyppi)

        hankealueService.mergeAlueetToHanke(hanke.alueet, entity)
    }

    /**
     * Creates a temporary id-to-existingyhteystieto -map. It can be used to find quickly whether an
     * incoming Yhteystieto is new or already in the database. (Also, copyYhteystietosToEntity() and
     * its subfunctions remove entries from such map as they process them, and any remaining entries
     * in it are considered as to be removed.) The given hankeEntity and its yhteystietos MUST be in
     * persisted state (have their id's set).
     */
    private fun prepareMapOfExistingYhteystietos(
        hankeEntity: HankeEntity
    ): MutableMap<Int, HankeYhteystietoEntity> {
        if (hankeEntity.yhteystiedot.any { it.id == null }) {
            throw DatabaseStateException(
                "A persisted HankeYhteystietoEntity somehow missing id, Hanke id ${hankeEntity.id}"
            )
        }
        return hankeEntity.yhteystiedot.associateBy { it.id!! }.toMutableMap()
    }

    /**
     * Transfers yhteystieto fields from domain to (new or existing) entity object, combines the
     * three lists into one list, and sets the audit fields as relevant.
     */
    private fun copyYhteystietosToEntity(
        hanke: ModifyHankeRequest,
        entity: HankeEntity,
        userid: String,
        loggingEntryHolder: YhteystietoLoggingEntryHolder,
        existingYTs: MutableMap<Int, HankeYhteystietoEntity>
    ) {
        // Note, if the incoming data indicates it is an already saved yhteystieto (id-field is
        // set), should try to transfer the business fields to the same old corresponding entity.
        // Pretty much a must in order to preserve createdBy and createdAt field values without
        // having to rely on the client-side to hold the values for us (bad design), which would
        // also require checks on those (to prevent tampering).

        hanke.yhteystiedotByType().forEach { (type, yhteystiedot) ->
            processIncomingHankeYhteystietosOfSpecificTypeToEntity(
                yhteystiedot,
                entity,
                type,
                userid,
                existingYTs,
                loggingEntryHolder
            )
        }

        // If there is anything left in the existingYTs map, they have been removed in the incoming
        // data, so remove them from the entity's list and make the back-reference null (and thus
        // delete from the database).
        for (hankeYht in existingYTs.values) {
            entity.removeYhteystieto(hankeYht)
            loggingEntryHolder.addLogEntriesForEvent(
                operation = Operation.DELETE,
                oldEntity = hankeYht,
                newEntity = null,
                userId = userid,
            )
        }
    }

    private fun processIncomingHankeYhteystietosOfSpecificTypeToEntity(
        yhteystiedot: List<ModifyHankeYhteystietoRequest>,
        hankeEntity: HankeEntity,
        contactType: ContactType,
        userid: String,
        existingYTs: MutableMap<Int, HankeYhteystietoEntity>,
        loggingEntryHolder: YhteystietoLoggingEntryHolder
    ) {
        for (hankeYht in yhteystiedot) {
            // Is the incoming Yhteystieto new (does not have id, create new) or old (has id, update
            // existing)?
            if (hankeYht.id == null) {
                // New Yhteystieto
                // Note: don't need to (and can not) create audit-log entries during this create
                // processing; they are done later, after the whole hanke has been saved and new
                // yhteystietos got their db-ids.
                processCreateYhteystieto(hankeYht, contactType, userid, hankeEntity)
            } else {
                // Should be an existing Yhteystieto
                processUpdateYhteystieto(
                    hankeYht,
                    existingYTs,
                    userid,
                    hankeEntity,
                    loggingEntryHolder
                )
            }
        }
    }

    private fun processCreateYhteystieto(
        hankeYht: ModifyHankeYhteystietoRequest,
        contactType: ContactType,
        userid: String,
        hankeEntity: HankeEntity
    ) {
        val hankeYhtEntity =
            HankeYhteystietoEntity.fromDomain(hankeYht, contactType, userid, hankeEntity)
        hankeYht.yhteyshenkilot.forEach { kayttajaId ->
            addYhteyshenkilo(kayttajaId, hankeEntity.id, hankeYhtEntity)
        }
        hankeEntity.addYhteystieto(hankeYhtEntity)
        // Logging of creating new yhteystietos is done after the hanke gets saved.
    }

    /** Incoming contact update must exist in previously saved contacts. */
    private fun processUpdateYhteystieto(
        request: ModifyHankeYhteystietoRequest,
        existingYTs: MutableMap<Int, HankeYhteystietoEntity>,
        userid: String,
        hanke: HankeIdentifier,
        loggingEntryHolder: YhteystietoLoggingEntryHolder
    ) {
        val incomingId: Int = request.id!!
        val existingYT: HankeYhteystietoEntity =
            existingYTs[incomingId] ?: throw HankeYhteystietoNotFoundException(hanke, incomingId)

        // Check if anything actually changed (UI can send all data as is on every request)
        if (!areEqualIncomingVsExistingYhteystietos(request, existingYT)) {
            // Record old data as JSON for change logging (and id for other purposes)
            val previousEntityData = existingYT.cloneWithMainFields()
            previousEntityData.id = existingYT.id

            existingYT.nimi = request.nimi
            existingYT.email = request.email
            existingYT.ytunnus = request.ytunnus
            existingYT.puhelinnumero = request.puhelinnumero
            request.organisaatioNimi?.let { existingYT.organisaatioNimi = request.organisaatioNimi }
            request.osasto?.let { existingYT.osasto = request.osasto }

            // (Not changing createdBy/At fields)
            existingYT.modifiedByUserId = userid
            existingYT.modifiedAt = getCurrentTimeUTCAsLocalTime()
            // (Not touching the id or hanke fields)

            loggingEntryHolder.addLogEntriesForEvent(
                operation = Operation.UPDATE,
                oldEntity = previousEntityData,
                newEntity = existingYT,
                userId = userid,
            )
        }

        val existingKayttajaIds = existingYT.yhteyshenkilot.map { it.hankeKayttaja.id }
        val kayttajatToAdd = request.yhteyshenkilot.filterNot { existingKayttajaIds.contains(it) }
        val kayttajatToRemove =
            existingYT.yhteyshenkilot.filterNot {
                request.yhteyshenkilot.contains(it.hankeKayttaja.id)
            }

        logger.info {
            "Add new yhteyshenkilot to yhteystieto ${existingYT.id} for hankekayttajat $kayttajatToAdd"
        }
        kayttajatToAdd.forEach { addYhteyshenkilo(it, hanke.id, existingYT) }
        logger.info {
            "Remove yhteyshenkilot from yhteystieto ${existingYT.id} for hankekayttajat $kayttajatToRemove"
        }
        kayttajatToRemove.forEach { removeYhteyshenkilo(it, existingYT) }

        // Remove the corresponding entry from the map. (Afterward, the entries remaining in
        // the map were not in the incoming data, so should be removed from the database.)
        existingYTs.remove(incomingId)
    }

    private fun addYhteyshenkilo(
        kayttajaId: UUID,
        hankeId: Int,
        hankeYhtEntity: HankeYhteystietoEntity,
    ) {
        val kayttaja = hankeKayttajaService.getKayttajaForHanke(kayttajaId, hankeId)
        val yhteyshenkilo =
            HankeYhteyshenkiloEntity(hankeKayttaja = kayttaja, hankeYhteystieto = hankeYhtEntity)
        hankeYhtEntity.yhteyshenkilot.add(yhteyshenkilo)
    }

    private fun removeYhteyshenkilo(
        yhteyshenkilo: HankeYhteyshenkiloEntity,
        existingYT: HankeYhteystietoEntity,
    ) {
        val kayttaja = yhteyshenkilo.hankeKayttaja
        kayttaja.yhteyshenkilot.remove(yhteyshenkilo)
        existingYT.yhteyshenkilot.remove(yhteyshenkilo)
    }

    private fun areEqualIncomingVsExistingYhteystietos(
        incoming: Yhteystieto,
        existing: HankeYhteystietoEntity
    ): Boolean {
        if (incoming.nimi != existing.nimi) return false
        if (incoming.email != existing.email) return false
        if (incoming.puhelinnumero != existing.puhelinnumero) return false
        if (incoming.organisaatioNimi != existing.organisaatioNimi) return false
        if (incoming.osasto != existing.osasto) return false
        return true
    }

    /**
     * Creates the entry holder object and initializes the old Yhteystieto id set (so that it can
     * know later which yhteystietos are created during the ongoing request).
     */
    private fun prepareLogging(entity: HankeEntity): YhteystietoLoggingEntryHolder {
        val loggingEntryHolder = YhteystietoLoggingEntryHolder()
        loggingEntryHolder.initWithOldYhteystietos(entity.yhteystiedot)
        return loggingEntryHolder
    }

    /**
     * Handles post-processing of logging entries about restricted actions. Applies request's IP to
     * all given logging entries, saves them, and creates and throws an exception which indicates
     * that restricted yhteystietos can not be changed/deleted.
     */
    private fun postProcessAndSaveLoggingForRestrictions(
        loggingEntryHolderForRestrictedActions: YhteystietoLoggingEntryHolder
    ) {
        loggingEntryHolderForRestrictedActions.saveLogEntries(hankeLoggingService)
        val idList = loggingEntryHolderForRestrictedActions.objectIds()
        throw HankeYhteystietoProcessingRestrictedException(
            "Can not modify/delete yhteystieto which has data processing restricted (id: $idList)"
        )
    }

    /**
     * Handles logging of all newly created Yhteystietos, applies request's IP to all log entries,
     * and saves all the log entries.
     *
     * Do not use this for the "restricted action" log events; see
     * [postProcessAndSaveLoggingForRestrictions] for that.
     */
    private fun postProcessAndSaveLogging(
        loggingEntryHolder: YhteystietoLoggingEntryHolder,
        savedHankeEntity: HankeEntity,
        userid: String
    ) {
        // It would be possible to process all operation types the same way afterward like for
        // creating new yhteystietos, but that would cause some (relatively) minor extra work, and,
        // the proper solution would be to handle personal data access in its own separate service
        // and do the logging there independently... So, the current way of doing things should be
        // good enough for now.
        loggingEntryHolder.addLogEntriesForNewYhteystietos(savedHankeEntity.yhteystiedot, userid)
        loggingEntryHolder.saveLogEntries(hankeLoggingService)
    }

    private fun saveCreatedHanke(
        entity: HankeEntity,
        perustaja: HankePerustaja,
        securityContext: SecurityContext,
    ): Hanke {
        val savedHankeEntity = hankeRepository.save(entity)
        hankeKayttajaService.addHankeFounder(savedHankeEntity.id, perustaja, securityContext)

        return createHankeDomainObjectFromEntity(savedHankeEntity).also {
            hankeLoggingService.logCreate(it, securityContext.userId())
        }
    }

    private fun createNewEntity(
        nimi: String,
        generated: Boolean,
        currentUserId: String,
    ): HankeEntity =
        HankeEntity(
            hankeTunnus = hanketunnusService.newHanketunnus(),
            nimi = nimi,
            onYKTHanke = null,
            generated = generated,
            status = HankeStatus.DRAFT,
            version = 0,
            createdByUserId = currentUserId,
            createdAt = getCurrentTimeUTCAsLocalTime(),
        )

    /**
     * Autogenerated hanke based on application data.
     * - Generated flag true.
     * - Hanke name is same as application name (limited to first 100 characters).
     * - HankeFounder generated from application data orderer.
     * - Hankealueet are created from the application areas.
     */
    private fun generateHankeFrom(
        cableReportWithoutHanke: CableReportWithoutHanke,
        securityContext: SecurityContext,
    ): Hanke =
        with(cableReportWithoutHanke) {
            val alueet = HankealueService.createHankealueetFromCableReport(applicationData)
            val nimi = limitHankeName(applicationData.name)
            val perustaja = applicationData.ordererAsFounder()

            val entity = createNewEntity(nimi, true, securityContext.userId())

            entity.alueet.addAll(hankealueService.createAlueetFromCreateRequest(alueet, entity))

            calculateTormaystarkastelu(
                alueet,
                entity.alueet.mapNotNull { alue -> alue.geometriat }.toSet(),
                entity
            )

            return saveCreatedHanke(entity, perustaja, securityContext)
        }

    private fun limitHankeName(name: String): String =
        if (name.length > MAXIMUM_HANKE_NIMI_LENGTH) {
            logger.info {
                "Hanke name too long, limited to first $MAXIMUM_HANKE_NIMI_LENGTH characters."
            }
            name.take(MAXIMUM_HANKE_NIMI_LENGTH)
        } else {
            name
        }

    private fun CableReportApplicationData.ordererAsFounder(): HankePerustaja =
        findOrderer()?.toHankePerustaja() ?: throw HankeArgumentException("Orderer not found.")
}

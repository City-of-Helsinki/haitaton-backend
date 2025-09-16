package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentService
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankePerustaja
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.domain.Hankevaihe
import fi.hel.haitaton.hanke.domain.ModifyHankeRequest
import fi.hel.haitaton.hanke.domain.ModifyHankeYhteystietoRequest
import fi.hel.haitaton.hanke.domain.Yhteystieto
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusMetaData
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusDataException
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusalue
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
import fi.hel.haitaton.hanke.logging.HankeLoggingService
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.YhteystietoLoggingEntryHolder
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.validation.HankePublicValidator
import java.time.LocalDate
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
    private val hankeMapGridService: HankeMapGridService,
    private val hankeMapperService: HankeMapperService,
    private val hakemusService: HakemusService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val hankeAttachmentService: HankeAttachmentService,
    private val geometriatDao: GeometriatDao,
) {

    @Transactional(readOnly = true)
    fun findIdentifier(hankeTunnus: String): HankeIdentifier? =
        hankeRepository.findOneByHankeTunnus(hankeTunnus)

    @Transactional(readOnly = true)
    fun getHankeApplications(hankeTunnus: String): List<HakemusMetaData> =
        hankeRepository.findByHankeTunnus(hankeTunnus)?.let { entity ->
            entity.hakemukset.map { hakemus -> hakemus.toMetadata() }
        } ?: throw HankeNotFoundException(hankeTunnus)

    @Transactional(readOnly = true)
    fun loadHanke(hankeTunnus: String): Hanke? =
        hankeRepository.findByHankeTunnus(hankeTunnus)?.let { hankeMapperService.domainFrom(it) }

    @Transactional(readOnly = true)
    fun loadPublicHanke(): List<Hanke> =
        hankeRepository.findAllByStatus(HankeStatus.PUBLIC).map {
            hankeMapperService.domainFrom(it)
        }

    @Transactional(readOnly = true)
    fun loadPublicHankeInGridCells(
        startDate: LocalDate,
        endDate: LocalDate,
        cells: List<GridCell>,
    ): List<Hanke> =
        cells
            .map { hankeMapGridService.loadPublicHankeInGridCell(it.x, it.y) }
            .flatten()
            .filter {
                (it.alkuPvm?.toLocalDate()?.let { alkuLocalDate -> alkuLocalDate <= endDate }
                    ?: false) &&
                    (it.loppuPvm?.toLocalDate()?.let { loppuLocalDate ->
                        loppuLocalDate >= startDate
                    } ?: false)
            }
            .distinctBy { it.hankeTunnus }

    @Transactional(readOnly = true)
    fun loadPublicHankeByHankeTunnus(hankeTunnus: String): Hanke =
        hankeRepository
            .findByHankeTunnus(hankeTunnus)
            ?.takeIf { it.status == HankeStatus.PUBLIC }
            ?.let { hankeMapperService.domainFrom(it) }
            ?: throw PublicHankeNotFoundException(hankeTunnus)

    @Transactional(readOnly = true)
    fun loadHankeById(id: Int): Hanke? =
        hankeRepository.findByIdOrNull(id)?.let { hankeMapperService.domainFrom(it) }

    @Transactional(readOnly = true)
    fun loadHankkeetByIds(ids: List<Int>) =
        hankeRepository.findAllById(ids).map { hankeMapperService.domainFrom(it) }

    @Transactional
    fun createHanke(request: CreateHankeRequest, securityContext: SecurityContext): Hanke {
        val entity = createNewEntity(request.nimi, false, securityContext.userId())

        return saveCreatedHanke(entity, request.perustaja, securityContext)
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
        // a hanke with a johtoselvityshakemus is in the RAKENTAMINEN phase at the beginning
        hanke.vaihe = Hankevaihe.RAKENTAMINEN
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

        val originalEndDate = entity.endDate()
        val hankeBeforeUpdate = hankeMapperService.domainFrom(entity)

        val existingYTs = prepareMapOfExistingYhteystietos(entity)

        val loggingEntryHolder = prepareLogging(entity)
        // Transfer field values from request object to entity object
        updateEntityFieldsFromRequest(hanke, entity, userId)
        copyYhteystietosToEntity(hanke, entity, userId, loggingEntryHolder, existingYTs)

        // Set relevant audit fields:
        // NOTE: flags are NOT copied from incoming data, as they are set by internal logic.
        // Special fields; handled "manually"..
        entity.version = entity.version?.inc() ?: 1
        // (Not changing createdBy/At fields.)
        entity.modifiedByUserId = userId
        entity.modifiedAt = getCurrentTimeUTCAsLocalTime()
        entity.generated = false

        updateTormaystarkastelut(entity.alueet)
        entity.status = decideNewHankeStatus(entity)

        if (entity.endDate() != originalEndDate) {
            entity.sentReminders = arrayOf()
        }

        val savedHankeEntity = hankeRepository.saveAndFlush(entity)

        assertHakemusCompatibility(entity)

        postProcessAndSaveLogging(loggingEntryHolder, savedHankeEntity, userId)

        return hankeMapperService.domainFrom(entity).also {
            hankeLoggingService.logUpdate(hankeBeforeUpdate, it, userId)
        }
    }

    @Transactional
    fun deleteHanke(hankeTunnus: String, userId: String) {
        val hanke = loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)
        val hakemukset = getHankeApplications(hankeTunnus)

        if (anyNonCancelledHakemusProcessingInAllu(hakemukset)) {
            throw HankeAlluConflictException(
                "Hanke ${hanke.hankeTunnus} has hakemus in Allu processing. Cannot delete."
            )
        }

        hakemukset.forEach { hakemus -> hakemusService.delete(hakemus.id, userId) }

        hankeAttachmentService.deleteAllAttachments(hanke)

        hankeRepository.deleteById(hanke.id)
        hankeLoggingService.logDelete(hanke, userId)
    }

    private fun anyNonCancelledHakemusProcessingInAllu(hakemukset: List<HakemusMetaData>): Boolean =
        hakemukset.any {
            logger.info { "Hakemus ${it.id} has alluStatus ${it.alluStatus}" }
            !hakemusService.isStillPending(it.alluid, it.alluStatus) &&
                !hakemusService.isCancelled(it.alluStatus)
        }

    private fun assertHakemusCompatibility(entity: HankeEntity) {
        val hakemukset = hakemusService.hankkeenHakemukset(entity.hankeTunnus)
        val hakemusalueet = hakemukset.flatMap { it.applicationData.areas ?: listOf() }

        hakemusService.assertGeometryCompatibility(entity.id, hakemusalueet)
        assertDateCompatibility(entity, hakemukset)
    }

    /** Assert that the hakemus dates are compatible with the hanke area geometries. */
    private fun assertDateCompatibility(entity: HankeEntity, hakemukset: List<Hakemus>) =
        hakemukset.forEach { hakemus ->
            when (val data = hakemus.applicationData) {
                is JohtoselvityshakemusData -> {
                    data.areas?.let { assertDateCompatibility(entity, data, hakemus, it) }
                }
                is KaivuilmoitusData -> {
                    data.areas?.let { assertDateCompatibility(entity, data, hakemus, it) }
                }
            }
        }

    private fun assertDateCompatibility(
        hanke: HankeEntity,
        hakemusData: KaivuilmoitusData,
        hakemus: Hakemus,
        alueet: List<KaivuilmoitusAlue>,
    ) {
        val hankealueet = hanke.alueet.associateBy { it.id }
        val hakemusStartDate = hakemusData.startTime?.toLocalDate()
        val hakemusEndDate = hakemusData.endTime?.toLocalDate()

        alueet.forEachIndexed { i, hakemusalue ->
            val hankealue =
                hankealueet[hakemusalue.hankealueId]
                    ?: throw InvalidHakemusDataException(listOf("areas[$i].hankealueId"))
            hakemusStartDate?.let {
                if (hankealue.haittaAlkuPvm?.isAfter(it) == true) {
                    throw HankeArgumentException(
                        "Hankealue doesn't cover the hakemus dates. " +
                            "Hakemusalue $i: $it - $hakemusEndDate. " +
                            "Hankealue ${hankealue.id}: ${hankealue.haittaAlkuPvm} - ${hankealue.haittaLoppuPvm}. " +
                            "${hanke.logString()}, ${hakemus.logString()}"
                    )
                }
            }
            hakemusEndDate?.let {
                if (hankealue.haittaLoppuPvm?.isBefore(it) == true) {
                    throw HankeArgumentException(
                        "Hankealue doesn't cover the hakemus dates. " +
                            "Hakemusalue $i: $hakemusStartDate - $it. " +
                            "Hankealue ${hankealue.id}: ${hankealue.haittaAlkuPvm} - ${hankealue.haittaLoppuPvm}. " +
                            "${hanke.logString()}, ${hakemus.logString()}"
                    )
                }
            }
        }
    }

    /**
     * Passes if for each hakemusalue there's at least one hankealue that covers it both spatially
     * and temporally.
     */
    private fun assertDateCompatibility(
        hanke: HankeEntity,
        hakemusData: JohtoselvityshakemusData,
        hakemus: Hakemus,
        alueet: List<JohtoselvitysHakemusalue>,
    ) {
        val hankealueet = hanke.alueet.associateBy { it.id }
        val hakemusStartDate = hakemusData.startTime?.toLocalDate()
        val hakemusEndDate = hakemusData.endTime?.toLocalDate()

        alueet.forEachIndexed { i, hakemusalue ->
            val matchingHankealueet =
                geometriatDao.matchingHankealueet(hanke.id, hakemusalue.geometry).mapNotNull {
                    hankealueet[it]
                }

            val noDateMatch =
                matchingHankealueet.all { hankealue ->
                    val hankealueStartsAfterHakemus =
                        hakemusStartDate != null &&
                            hankealue.haittaAlkuPvm?.isAfter(hakemusStartDate) == true
                    val hankealueEndsBeforeHakemus =
                        hakemusEndDate != null &&
                            hankealue.haittaLoppuPvm?.isBefore(hakemusEndDate) == true
                    hankealueStartsAfterHakemus || hankealueEndsBeforeHakemus
                }

            if (noDateMatch) {
                val hakemusDates = "$hakemusStartDate - $hakemusEndDate"
                val hankealueDates =
                    matchingHankealueet.joinToString {
                        "${it.id}: ${it.haittaAlkuPvm} - ${it.haittaLoppuPvm}"
                    }
                throw HankeArgumentException(
                    "No hankealue covers the hakemusalue. Hakemusalue $i: $hakemusDates. " +
                        "Hankealueet $hankealueDates. " +
                        "${hanke.logString()}, ${hakemus.logString()}"
                )
            }
        }
    }

    private fun updateTormaystarkastelut(alueet: List<HankealueEntity>) {
        for (alue in alueet) {
            hankealueService.updateTormaystarkastelu(alue)
        }
    }

    private fun decideNewHankeStatus(entity: HankeEntity): HankeStatus {
        val validationResult =
            HankePublicValidator.validateHankeHasMandatoryFields(
                hankeMapperService.domainFrom(entity)
            )

        return when (val status = entity.status) {
            HankeStatus.DRAFT ->
                if (validationResult.isOk()) {
                    HankeStatus.PUBLIC
                } else {
                    HankeStatus.DRAFT
                }
            HankeStatus.PUBLIC ->
                if (validationResult.isOk()) {
                    HankeStatus.PUBLIC
                } else {
                    logger.warn {
                        "A public hanke wasn't updated with missing or invalid fields. hankeTunnus=${entity.hankeTunnus} failedFields=${
                            validationResult.errorPaths().joinToString()
                        }"
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

    private fun updateEntityFieldsFromRequest(
        hanke: ModifyHankeRequest,
        entity: HankeEntity,
        currentUserId: String,
    ) {
        entity.onYKTHanke = hanke.onYKTHanke
        entity.nimi = hanke.nimi
        entity.kuvaus = hanke.kuvaus
        entity.vaihe = hanke.vaihe
        entity.tyomaaKatuosoite = hanke.tyomaaKatuosoite
        val newTyyppi = hanke.tyomaaTyyppi.minus(entity.tyomaaTyyppi)
        val removedTyyppi = entity.tyomaaTyyppi.minus(hanke.tyomaaTyyppi)
        entity.tyomaaTyyppi.removeAll(removedTyyppi)
        entity.tyomaaTyyppi.addAll(newTyyppi)

        hankealueService.mergeAlueetToHanke(hanke.alueet, entity, currentUserId)
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
        existingYTs: MutableMap<Int, HankeYhteystietoEntity>,
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
                loggingEntryHolder,
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
        loggingEntryHolder: YhteystietoLoggingEntryHolder,
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
                    loggingEntryHolder,
                )
            }
        }
    }

    private fun processCreateYhteystieto(
        hankeYht: ModifyHankeYhteystietoRequest,
        contactType: ContactType,
        userid: String,
        hankeEntity: HankeEntity,
    ) {
        val hankeYhtEntity =
            HankeYhteystietoEntity.fromDomain(hankeYht, contactType, userid, hankeEntity)
        hankeYht.yhteyshenkilot.forEach { kayttajaId ->
            addYhteyshenkilo(kayttajaId, hankeEntity.id, hankeYhtEntity)
        }
        hankeEntity.yhteystiedot.add(hankeYhtEntity)
        hankeYhtEntity.hanke = hankeEntity
        // Logging of creating new yhteystietos is done after the hanke gets saved.
    }

    /** Incoming contact update must exist in previously saved contacts. */
    private fun processUpdateYhteystieto(
        request: ModifyHankeYhteystietoRequest,
        existingYTs: MutableMap<Int, HankeYhteystietoEntity>,
        userid: String,
        hanke: HankeIdentifier,
        loggingEntryHolder: YhteystietoLoggingEntryHolder,
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
        existing: HankeYhteystietoEntity,
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
     * Handles logging of all newly created Yhteystietos, applies request's IP to all log entries,
     * and saves all the log entries.
     */
    private fun postProcessAndSaveLogging(
        loggingEntryHolder: YhteystietoLoggingEntryHolder,
        savedHankeEntity: HankeEntity,
        userid: String,
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

        return hankeMapperService.domainFrom(savedHankeEntity).also {
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

    private fun limitHankeName(name: String): String =
        if (name.length > MAXIMUM_HANKE_NIMI_LENGTH) {
            logger.info {
                "Hanke name too long, limited to first $MAXIMUM_HANKE_NIMI_LENGTH characters."
            }
            name.take(MAXIMUM_HANKE_NIMI_LENGTH)
        } else {
            name
        }
}

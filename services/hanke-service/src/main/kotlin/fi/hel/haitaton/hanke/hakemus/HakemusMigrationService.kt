package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.ExcavationNotificationData
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.KayttajakutsuEntity
import fi.hel.haitaton.hanke.permissions.KayttajakutsuRepository
import fi.hel.haitaton.hanke.permissions.PermissionService
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class HakemusMigrationService(
    val hankeRepository: HankeRepository,
    val hankekayttajaRepository: HankekayttajaRepository,
    val kayttajakutsuRepository: KayttajakutsuRepository,
    val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
    val permissionService: PermissionService,
) {

    @Transactional
    fun migrateOneHanke(hankeId: Int) {
        val hanke = hankeRepository.getReferenceById(hankeId)

        hanke.hakemukset.singleOrNull()?.let { hakemus: ApplicationEntity ->
            logger.info { "Migrating hakemus. ${hakemus.logString()}, ${hanke.logString()}" }

            val existingKayttajat = hankekayttajaRepository.findByHankeId(hanke.id)
            logger.info {
                if (existingKayttajat.isNotEmpty()) {
                    "Hanke already has users. ${existingKayttajat.size} users with IDs: " +
                        existingKayttajat.map { it.id }.joinToString()
                } else {
                    "Hanke does not have users."
                }
            }
            val existingKayttajaEmails = existingKayttajat.map { it.sahkoposti }.toMutableSet()

            val founder =
                createFounderKayttaja(hanke.id, hakemus.applicationData, existingKayttajaEmails)
            if (founder != null) {
                logger.info { "Created founder with id ${founder.id}" }
                existingKayttajaEmails.add(founder.sahkoposti)
            } else {
                logger.info { "No founder found in hakemus data." }
            }
            val otherKayttajat =
                createOtherKayttajat(hanke.id, hakemus.applicationData, existingKayttajaEmails)
            logger.info {
                "Created hankekayttajat for other contacts. ${otherKayttajat.size} users with IDs: " +
                    otherKayttajat.map { it.id }.joinToString()
            }
            createYhteystiedot(
                hakemus,
                (otherKayttajat + founder + existingKayttajat).filterNotNull()
            )
            hakemus.applicationData = clearCustomers(hakemus.applicationData)
        } ?: logger.info { "No hakemus for hanke. ${hanke.logString()}" }
    }

    private fun createYhteystiedot(
        applicationEntity: ApplicationEntity,
        kayttajat: List<HankekayttajaEntity>
    ) {
        val customersByRoles = applicationEntity.applicationData.customersByRole()
        for ((rooli, customerWithContacts) in customersByRoles) {
            val customer = customerWithContacts.customer

            val yhteystieto =
                hakemusyhteystietoRepository.save(
                    HakemusyhteystietoEntity(
                        tyyppi = customer.type ?: continue,
                        rooli = rooli,
                        nimi = customer.name.defaultIfNullOrBlank(),
                        sahkoposti = customer.email.defaultIfNullOrBlank(),
                        puhelinnumero = customer.phone.defaultIfNullOrBlank(),
                        ytunnus = customer.registryKey,
                        application = applicationEntity
                    )
                )

            logger.info {
                "Created an yhteystieto for the hakemus. yhteystietoId=${yhteystieto.id}, ${applicationEntity.logString()}"
            }

            val yhteyshenkilot =
                customerWithContacts.contacts
                    .groupBy { it.email }
                    .mapNotNull { (email, contacts) ->
                        val contact = contacts.contactWithLeastMissingFields()
                        contact?.let {
                            HakemusyhteyshenkiloEntity(
                                hakemusyhteystieto = yhteystieto,
                                hankekayttaja = kayttajat.find { it.sahkoposti == email }!!,
                                tilaaja = contact.orderer,
                            )
                        }
                    }
            val saved = hakemusyhteyshenkiloRepository.saveAll(yhteyshenkilot)
            if (customerWithContacts.contacts.size != saved.size) {
                logger.info {
                    "From ${customerWithContacts.contacts.size} contacts we got ${saved.size} yhteystiedot."
                }
            }
            logger.info { "Created yhteystiedot. IDs: ${saved.map { it.id }.joinToString()}" }
        }
    }

    /** Until now, the founder has been marked as the orderer. */
    fun createFounderKayttaja(
        hankeId: Int,
        applicationData: ApplicationData,
        existingKayttajaEmails: Set<String>
    ): HankekayttajaEntity? {
        val orderer: Contact =
            findOrderer(applicationData) ?: return null.also { logger.warn { "No founder found." } }
        if (orderer.email.isNullOrBlank()) {
            logger.warn { "Founder has no email, skipping creating kayttaja." }
            return null
        }
        if (orderer.email in existingKayttajaEmails) {
            logger.info { "Founder already has a hankekayttaja." }
            return null
        }
        val permission = permissionService.findByHankeId(hankeId).singleOrNull()

        val kayttaja =
            HankekayttajaEntity(
                hankeId = hankeId,
                etunimi = orderer.firstName.defaultIfNullOrBlank(),
                sukunimi = orderer.lastName.defaultIfNullOrBlank(),
                sahkoposti = orderer.email,
                // In production, there's an orderer with a blank phone number
                puhelin = orderer.phone.defaultIfNullOrBlank(),
                permission = permission,
            )
        return hankekayttajaRepository.save(kayttaja)
    }

    @Transactional
    fun createOtherKayttajat(
        hankeId: Int,
        applicationData: ApplicationData,
        existingKayttajaEmails: Set<String>,
    ): List<HankekayttajaEntity> {
        val byEmail: Map<String, List<Contact>> =
            applicationData
                .customersWithContacts()
                .flatMap { it.contacts }
                .filter { !it.email.isNullOrBlank() }
                .filter { it.email !in existingKayttajaEmails }
                .groupBy { it.email!! }

        logger.info { "Found ${byEmail.size} other contacts to migrate." }

        val entities =
            byEmail.map { (email, contacts) ->
                val etunimi = contacts.mapNotNull { it.firstName }.mode().defaultIfNullOrBlank()
                val sukunimi = contacts.mapNotNull { it.lastName }.mode().defaultIfNullOrBlank()
                HankekayttajaEntity(
                    hankeId = hankeId,
                    etunimi = etunimi,
                    sukunimi = sukunimi,
                    puhelin = contacts.mapNotNull { it.phone }.mode().defaultIfNullOrBlank(),
                    sahkoposti = email,
                    kutsuttuEtunimi = etunimi,
                    kutsuttuSukunimi = sukunimi,
                    permission = null,
                )
            }
        val saved = hankekayttajaRepository.saveAll(entities)
        for (entity in saved) {
            val kutsu = kayttajakutsuRepository.save(KayttajakutsuEntity.create(entity))
            entity.kayttajakutsu = kutsu
        }
        return saved
    }

    companion object {
        internal fun clearCustomers(applicationData: ApplicationData): ApplicationData =
            when (applicationData) {
                is CableReportApplicationData ->
                    applicationData.copy(
                        customerWithContacts = null,
                        contractorWithContacts = null,
                        propertyDeveloperWithContacts = null,
                        representativeWithContacts = null,
                    )
                is ExcavationNotificationData ->
                    applicationData.copy(
                        customerWithContacts = null,
                        contractorWithContacts = null,
                        propertyDeveloperWithContacts = null,
                        representativeWithContacts = null,
                    )
            }

        /**
         * Find the contact with the orderer flag set.
         *
         * If there are several orderers, find the contact with the most filled fields from those.
         */
        internal fun findOrderer(applicationData: ApplicationData): Contact? {
            val orderers =
                applicationData
                    .customersWithContacts()
                    .flatMap { it.contacts }
                    .filter { it.orderer }

            return orderers.contactWithLeastMissingFields()
        }

        internal fun List<Contact>.contactWithLeastMissingFields(): Contact? = minByOrNull {
            var missingFields = 0
            if (it.firstName.isNullOrBlank()) {
                missingFields += 1
            }
            if (it.lastName.isNullOrBlank()) {
                missingFields += 1
            }
            if (it.email.isNullOrBlank()) {
                missingFields += 1
            }
            if (it.phone.isNullOrBlank()) {
                missingFields += 1
            }

            missingFields
        }

        internal fun String?.defaultIfNullOrBlank(): String = if (isNullOrBlank()) "-" else this

        internal fun List<String>.mode(): String? =
            filter { it.isNotBlank() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }
}

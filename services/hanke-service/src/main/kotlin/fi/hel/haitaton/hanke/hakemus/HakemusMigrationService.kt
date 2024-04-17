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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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

        hanke.hakemukset.singleOrNull()?.let {
            val founder = createFounderKayttaja(hanke.id, it.applicationData)
            val otherKayttajat = createOtherKayttajat(it.applicationData, founder, hanke.id)
            createYhteystiedot(it, (otherKayttajat + founder).filterNotNull())
            it.applicationData = clearCustomers(it.applicationData)
        }
    }

    private fun createYhteystiedot(
        applicationEntity: ApplicationEntity,
        kayttajat: List<HankekayttajaEntity>
    ) {
        for ((rooli, customerWithContacts) in applicationEntity.applicationData.customersByRole()) {
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
            hakemusyhteyshenkiloRepository.saveAll(yhteyshenkilot)
        }
    }

    /** Until now, the founder has been marked as the orderer. */
    fun createFounderKayttaja(
        hankeId: Int,
        applicationData: ApplicationData
    ): HankekayttajaEntity? {
        val orderer: Contact = findOrderer(applicationData) ?: return null
        if (orderer.email.isNullOrBlank()) return null
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
        applicationData: ApplicationData,
        founder: HankekayttajaEntity?,
        hankeId: Int,
    ): List<HankekayttajaEntity> {
        val byEmail: Map<String, List<Contact>> =
            applicationData
                .customersWithContacts()
                .flatMap { it.contacts }
                .filter { !it.email.isNullOrBlank() }
                .filter { it.email != founder?.sahkoposti }
                .groupBy { it.email!! }

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

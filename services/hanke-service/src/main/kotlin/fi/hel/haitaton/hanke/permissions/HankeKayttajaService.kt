package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeArgumentException
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.domain.Hanke
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class HankeKayttajaService(
    private val hankeKayttajaRepository: HankeKayttajaRepository,
    private val kayttajaTunnisteRepository: KayttajaTunnisteRepository,
) {

    @Transactional
    fun saveNewTokensFromApplication(applicationData: ApplicationData, hankeId: Int) {
        logger.info { "Creating tokens for application" }
        val contacts =
            applicationData
                .customersWithContacts()
                .flatMap { it.contacts }
                .mapNotNull { userContactOrNull(it.fullName(), it.email) }
        logger.info { "Found contacts, there are ${contacts.size} contacts" }

        filterNewContacts(hankeId, contacts).forEach { contact -> createToken(hankeId, contact) }
        logger.info { "Created tokens for application" }
    }

    @Transactional
    fun saveNewTokensFromHanke(hanke: Hanke) {
        val hankeId = hanke.id ?: throw HankeArgumentException("Hanke without id")
        val contacts =
            hanke
                .extractYhteystiedot()
                .flatMap { it.alikontaktit }
                .mapNotNull { userContactOrNull(it.fullName(), it.email) }

        filterNewContacts(hankeId, contacts).forEach { contact -> createToken(hankeId, contact) }
    }

    private fun createToken(hankeId: Int, contact: UserContact) {
        logger.info { "Creating a new user token, hankeId=$hankeId" }
        val token = KayttajaTunnisteEntity.create()
        logger.info { "Saving the new user token, hankeId=$hankeId" }
        val kayttajaTunnisteEntity = kayttajaTunnisteRepository.save(token)
        logger.info { "Saved the new user token, id=${kayttajaTunnisteEntity.id}" }

        val user =
            hankeKayttajaRepository.save(
                HankeKayttajaEntity(
                    hankeId = hankeId,
                    nimi = contact.name,
                    sahkoposti = contact.email,
                    permission = null,
                    kayttajaTunniste = kayttajaTunnisteEntity
                )
            )
        logger.info { "Saved the user information, id=${user.id}" }
    }

    private fun userContactOrNull(name: String?, email: String?): UserContact? {
        return when {
            name.isNullOrBlank() || email.isNullOrBlank() -> null
            else -> UserContact(name, email)
        }
    }

    private fun filterNewContacts(hankeId: Int, contacts: List<UserContact>): List<UserContact> {
        logger.info {
            "Finding existing emails for filtering new contacts from ${contacts.size} contacts"
        }
        val existingEmails = hankeExistingEmails(hankeId, contacts)
        logger.info { "Found ${existingEmails.size} emails" }

        val newContacts =
            contacts
                .filter { contact -> !existingEmails.contains(contact.email) }
                .distinctBy { it.email }
        logger.info { "Found ${newContacts.size} new contacts" }
        return newContacts
    }

    private fun hankeExistingEmails(hankeId: Int, contacts: List<UserContact>): List<String> =
        hankeKayttajaRepository
            .findByHankeIdAndSahkopostiIn(hankeId, contacts.map { it.email })
            .map { it.sahkoposti }
}

data class UserContact(val name: String, val email: String)

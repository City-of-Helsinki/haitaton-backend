package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeArgumentException
import fi.hel.haitaton.hanke.application.ApplicationEntity
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
    fun saveNewTokensFromApplication(application: ApplicationEntity, hankeId: Int) {
        logger.info {
            "Creating users and user tokens for application ${application.id}, alluid=${application.alluid}}"
        }
        val contacts =
            application.applicationData
                .customersWithContacts()
                .flatMap { it.contacts }
                .mapNotNull { userContactOrNull(it.fullName(), it.email) }

        filterNewContacts(hankeId, contacts).forEach { contact -> createToken(hankeId, contact) }
    }

    @Transactional
    fun saveNewTokensFromHanke(hanke: Hanke) {
        logger.info {
            "Creating users and user tokens for hanke ${hanke.id}, hankeTunnus=${hanke.hankeTunnus}}"
        }
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
        val existingEmails = hankeExistingEmails(hankeId, contacts)

        val newContacts =
            contacts
                .filter { contact -> !existingEmails.contains(contact.email) }
                .distinctBy { it.email }
        logger.info {
            "From ${contacts.size} contacts, there were ${newContacts.size} new contacts."
        }
        return newContacts
    }

    private fun hankeExistingEmails(hankeId: Int, contacts: List<UserContact>): List<String> =
        hankeKayttajaRepository
            .findByHankeIdAndSahkopostiIn(hankeId, contacts.map { it.email })
            .map { it.sahkoposti }
}

data class UserContact(val name: String, val email: String)

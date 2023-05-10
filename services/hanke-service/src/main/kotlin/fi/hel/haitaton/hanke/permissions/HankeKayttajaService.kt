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
        logger.info { "Saving new tokens from application." }
        val contacts =
            applicationData
                .customersWithContacts()
                .flatMap { it.contacts }
                .mapNotNull { userContactOrNull(it.fullName(), it.email) }

        filterNewContacts(hankeId, contacts).forEach { contact -> createToken(hankeId, contact) }
        logger.info { "All application contact tokens saved." }
    }

    @Transactional
    fun saveNewTokensFromHanke(hanke: Hanke) {
        logger.info { "Saving new tokens from hanke." }
        val hankeId = hanke.id ?: throw HankeArgumentException("Hanke without id")
        val contacts =
            hanke
                .extractYhteystiedot()
                .flatMap { it.alikontaktit }
                .mapNotNull { userContactOrNull(it.fullName(), it.email) }

        filterNewContacts(hankeId, contacts).forEach { contact -> createToken(hankeId, contact) }
        logger.info { "All hanke contact tokens saved." }
    }

    private fun createToken(hankeId: Int, contact: UserContact) {
        logger.info { "Saving token for contact." }
        val kayttajaTunnisteEntity = kayttajaTunnisteRepository.save(KayttajaTunnisteEntity())

        hankeKayttajaRepository.save(
            HankeKayttajaEntity(
                hankeId = hankeId,
                nimi = contact.name,
                sahkoposti = contact.email,
                permission = null,
                kayttajaTunniste = kayttajaTunnisteEntity
            )
        )
        logger.info { "Saved token for contact." }
    }

    private fun userContactOrNull(name: String?, email: String?): UserContact? {
        return when {
            name.isNullOrBlank() || email.isNullOrBlank() -> null
            else -> UserContact(name, email)
        }
    }

    private fun filterNewContacts(hankeId: Int, contacts: List<UserContact>): List<UserContact> {
        val existingEmails = hankeExistingEmails(hankeId, contacts)

        return contacts
            .filter { contact -> !existingEmails.contains(contact.email) }
            .distinctBy { it.email }
    }

    private fun hankeExistingEmails(hankeId: Int, contacts: List<UserContact>): List<String> =
        hankeKayttajaRepository
            .findByHankeIdAndSahkopostiIn(hankeId, contacts.map { it.email })
            .map { it.sahkoposti }
}

data class UserContact(val name: String, val email: String)

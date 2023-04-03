package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.Contact
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HankeKayttajaService(
    private val hankeKayttajaRepository: HankeKayttajaRepository,
    private val kayttajaTunnisteRepository: KayttajaTunnisteRepository,
) {

    @Transactional
    fun saveNewTokensFromApplication(applicationData: ApplicationData, hankeId: Int) {
        // Contacts new for this application
        val newContacts =
            applicationData
                .customersWithContacts()
                .flatMap { it.contacts }
                .filter { !it.email.isNullOrBlank() && !it.name.isNullOrBlank() }

        // Emails that already exist for the hanke, from either the hanke contacts or from other
        // applications
        val existingEmails =
            hankeKayttajaRepository
                .findByHankeIdAndSahkopostiIn(hankeId, newContacts.map { it.email!! })
                .map { it.sahkoposti }

        val contactsToCreate =
            newContacts
                .filter { contact -> !existingEmails.contains(contact.email) }
                .distinctBy { it.email }
        contactsToCreate.forEach { createToken(hankeId, it) }
    }

    private fun createToken(hankeId: Int, contact: Contact) {
        val kayttajaTunnisteEntity = kayttajaTunnisteRepository.save(KayttajaTunnisteEntity())

        hankeKayttajaRepository.save(
            HankeKayttajaEntity(
                id = UUID.randomUUID(),
                hankeId = hankeId,
                nimi = contact.name!!,
                sahkoposti = contact.email!!,
                permission = null,
                kayttajaTunniste = kayttajaTunnisteEntity
            )
        )
    }
}

package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeArgumentException
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.domain.Hanke
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HankeKayttajaService(
    private val hankeKayttajaRepository: HankeKayttajaRepository,
    private val kayttajaTunnisteRepository: KayttajaTunnisteRepository,
) {

    @Transactional
    fun saveNewTokensFromApplication(applicationData: ApplicationData, hankeId: Int) {
        val contacts =
            applicationData
                .customersWithContacts()
                .flatMap { it.contacts }
                .mapNotNull { (name, email) ->
                    when {
                        name.isNullOrBlank() -> null
                        email.isNullOrBlank() -> null
                        else -> UserContactDto(name, email)
                    }
                }

        filterNewContacts(hankeId, contacts).forEach { contact -> createToken(hankeId, contact) }
    }

    @Transactional
    fun saveNewTokensFromHanke(hanke: Hanke) {
        val hankeId = hanke.id ?: throw HankeArgumentException("Hanke without id")
        val contacts =
            hanke
                .extractYhteystiedot()
                .flatMap { it.alikontaktit }
                .mapNotNull { person ->
                    val name = person.wholeName()
                    when {
                        name.isBlank() -> null
                        person.email.isBlank() -> null
                        else -> UserContactDto(name, person.email)
                    }
                }

        filterNewContacts(hankeId, contacts).forEach { contact -> createToken(hankeId, contact) }
    }

    private fun createToken(hankeId: Int, contact: UserContactDto) {
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
    }

    private fun filterNewContacts(
        hankeId: Int,
        contacts: List<UserContactDto>
    ): List<UserContactDto> {
        val existingEmails = hankeExistingEmails(hankeId, contacts)

        return contacts
            .filter { contact -> !existingEmails.contains(contact.email) }
            .distinctBy { it.email }
    }

    private fun hankeExistingEmails(hankeId: Int, contacts: List<UserContactDto>): List<String> =
        hankeKayttajaRepository
            .findByHankeIdAndSahkopostiIn(hankeId, contacts.map { it.email })
            .map { it.sahkoposti }
}

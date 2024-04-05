package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.isValidBusinessId
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.Validators

internal fun Hakemusyhteystieto.validateForErrors(path: String): ValidationResult =
    Validators.validate { Validators.notJustWhitespace(nimi, "$path.nimi") }
        .and { Validators.notJustWhitespace(sahkoposti, "$path.sahkoposti") }
        .and { Validators.notJustWhitespace(puhelinnumero, "$path.puhelinnumero") }
        .whenNotNull(ytunnus) { Validators.validateTrue(it.isValidBusinessId(), "$path.ytunnus") }
        .andAllIn(yhteyshenkilot, "$path.yhteyshenkilot", ::validateContactForErrors)

internal fun validateContactForErrors(yhteyshenkilo: Hakemusyhteyshenkilo, path: String) =
    with(yhteyshenkilo) {
        Validators.validate { Validators.notJustWhitespace(etunimi, "$path.etunimi") }
            .and { Validators.notJustWhitespace(sukunimi, "$path.sukunimi") }
            .and { Validators.notJustWhitespace(sahkoposti, "$path.sahkoposti") }
            .and { Validators.notJustWhitespace(puhelin, "$path.puhelin") }
    }

internal fun atMostOneOrderer(yhteystiedot: List<Hakemusyhteystieto>): ValidationResult =
    Validators.validateFalse(
        yhteystiedot.tilaajaCount() > 1,
        "customersWithContacts[].contacts[].orderer"
    )

internal fun List<Hakemusyhteystieto>.tilaajaCount() =
    flatMap { it.yhteyshenkilot }.count { it.tilaaja }

internal fun exactlyOneOrderer(yhteystiedot: List<Hakemusyhteystieto>): ValidationResult =
    Validators.validateTrue(
        yhteystiedot.tilaajaCount() == 1,
        "customersWithContacts[].contacts[].orderer"
    )

internal fun Hakemusyhteystieto.validateForMissing(path: String): ValidationResult =
    Validators.validate { Validators.notNull(tyyppi, "$path.tyyppi") }
        .and { Validators.notBlank(nimi, "$path.nimi") }
        .andAllIn(yhteyshenkilot, "$path.yhteyshenkilot", ::validateForMissing)

internal fun validateForMissing(contact: Hakemusyhteyshenkilo, path: String) =
    Validators.validate { Validators.notBlank(contact.kokoNimi(), "$path.firstName") }

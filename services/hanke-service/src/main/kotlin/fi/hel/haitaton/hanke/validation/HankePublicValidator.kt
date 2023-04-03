package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.validation.Validators.firstOf
import fi.hel.haitaton.hanke.validation.Validators.given
import fi.hel.haitaton.hanke.validation.Validators.notBlank
import fi.hel.haitaton.hanke.validation.Validators.notEmpty
import fi.hel.haitaton.hanke.validation.Validators.notNull
import fi.hel.haitaton.hanke.validation.Validators.notNullOrBlank
import fi.hel.haitaton.hanke.validation.Validators.notNullOrEmpty
import fi.hel.haitaton.hanke.validation.Validators.validate

/**
 * Validator for checking whether a hanke can be public. A hanke is considered public if all
 * mandatory fields are filled.
 */
object HankePublicValidator {

    fun validateHankeHasMandatoryFields(hanke: Hanke): ValidationResult {
        return validate { notNullOrBlank(hanke.nimi, "nimi") }
            .and { notNullOrBlank(hanke.kuvaus, "kuvaus") }
            .and { notNullOrBlank(hanke.tyomaaKatuosoite, "tyomaaKatuosoite") }
            .and { notNull(hanke.vaihe, "vaihe") }
            .and {
                given(hanke.vaihe == Vaihe.SUUNNITTELU) {
                    notNull(hanke.suunnitteluVaihe, "suunnitteluVaihe")
                }
            }
            .and { notEmpty(hanke.alueet, "alueet") }
            .andAllIn(hanke.alueet, "alueet", ::validateAlue)
            .and { notEmpty(hanke.omistajat, "omistajat") }
            .andAllIn(hanke.omistajat, "omistajat", ::validateYhteystieto)
            .andAllIn(hanke.rakennuttajat, "rakennuttajat", ::validateYhteystieto)
            .andAllIn(hanke.toteuttajat, "toteuttajat", ::validateYhteystieto)
    }

    private fun validateAlue(alue: Hankealue, path: String): ValidationResult =
        validate { notNull(alue.haittaAlkuPvm, "$path.haittaAlkuPvm") }
            .and { notNull(alue.haittaLoppuPvm, "$path.haittaLoppuPvm") }
            .and { notNull(alue.meluHaitta, "$path.meluHaitta") }
            .and { notNull(alue.polyHaitta, "$path.polyHaitta") }
            .and { notNull(alue.tarinaHaitta, "$path.tarinaHaitta") }
            .and { notNull(alue.kaistaHaitta, "$path.kaistaHaitta") }
            .and { notNull(alue.kaistaPituusHaitta, "$path.kaistaPituusHaitta") }
            .and {
                firstOf(
                    notNull(alue.geometriat, "$path.geometriat"),
                    notNull(
                        alue.geometriat?.featureCollection,
                        "$path.geometriat.featureCollection"
                    ),
                    notNullOrEmpty(
                        alue.geometriat?.featureCollection?.features,
                        "$path.geometriat.featureCollection.features"
                    ),
                )
            }

    /**
     * Mandatory fields after Yhteystiedot have been redone:
     * - Tyyppi
     * - Nimi
     * - Y-tunnus tai henkilötunnus
     * - Email
     *
     * For cantacs the mandatory fields are:
     * - Nimi
     * - Email
     */
    private fun validateYhteystieto(yhteystieto: HankeYhteystieto, path: String): ValidationResult =
        validate { notBlank(yhteystieto.nimi, "$path.etunimi") }
            .and { notBlank(yhteystieto.email, "$path.email") }
}

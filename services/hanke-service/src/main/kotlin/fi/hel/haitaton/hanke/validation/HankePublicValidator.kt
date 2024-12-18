package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.domain.Haittojenhallintasuunnitelma
import fi.hel.haitaton.hanke.domain.Haittojenhallintatyyppi
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YHTEISO
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YKSITYISHENKILO
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YRITYS
import fi.hel.haitaton.hanke.isValidBusinessId
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.validation.ValidationResult.Companion.whenNotNull
import fi.hel.haitaton.hanke.validation.Validators.firstOf
import fi.hel.haitaton.hanke.validation.Validators.notBlank
import fi.hel.haitaton.hanke.validation.Validators.notEmpty
import fi.hel.haitaton.hanke.validation.Validators.notNull
import fi.hel.haitaton.hanke.validation.Validators.notNullOrBlank
import fi.hel.haitaton.hanke.validation.Validators.notNullOrEmpty
import fi.hel.haitaton.hanke.validation.Validators.validate
import fi.hel.haitaton.hanke.validation.Validators.validateNull
import fi.hel.haitaton.hanke.validation.Validators.validateTrue

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
            .and { notEmpty(hanke.alueet, "alueet") }
            .andAllIn(hanke.alueet, "alueet", ::validateAlue)
            .and { notEmpty(hanke.omistajat, "omistajat") }
            .andAllIn(hanke.omistajat, "omistajat", ::validateYhteystieto)
            .andAllIn(hanke.rakennuttajat, "rakennuttajat", ::validateYhteystieto)
            .andAllIn(hanke.toteuttajat, "toteuttajat", ::validateYhteystieto)
            .andAllIn(hanke.muut, "muut", ::validateYhteystieto)
    }

    private fun validateAlue(alue: SavedHankealue, path: String): ValidationResult =
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
                        alue.geometriat?.featureCollection, "$path.geometriat.featureCollection"),
                    notNullOrEmpty(
                        alue.geometriat?.featureCollection?.features,
                        "$path.geometriat.featureCollection.features"),
                )
            }
            .and { notNullOrBlank(alue.nimi, "$path.nimi") }
            .andNotNull(alue.haittojenhallintasuunnitelma, "$path.haittojenhallintasuunnitelma") {
                hhs,
                p ->
                whenNotNull(alue.tormaystarkasteluTulos) { tt ->
                        validateHaittojenhallintasuunnitelmaLiikennemuodot(hhs, tt, p)
                    }
                    .and { validateHaittojenhallintasuunnitelmaCommonFields(hhs, p) }
            }

    private fun validateYhteystieto(yhteystieto: HankeYhteystieto, path: String): ValidationResult =
        validate { notBlank(yhteystieto.nimi, "$path.nimi") }
            .and { notBlank(yhteystieto.email, "$path.email") }
            .andWhen(yhteystieto.tyyppi in listOf(YRITYS, YHTEISO)) {
                validateTrue(yhteystieto.ytunnus.isValidBusinessId(), "$path.ytunnus")
            }
            .andWhen(yhteystieto.tyyppi == YKSITYISHENKILO) {
                validateNull(yhteystieto.ytunnus, "$path.ytunnus")
            }

    internal fun validateHaittojenhallintasuunnitelmaCommonFields(
        hhs: Haittojenhallintasuunnitelma,
        path: String
    ) =
        validate { notNullOrBlank(hhs[Haittojenhallintatyyppi.MUUT], "$path.MUUT") }
            .and { notNullOrBlank(hhs[Haittojenhallintatyyppi.YLEINEN], "$path.YLEINEN") }

    internal fun validateHaittojenhallintasuunnitelmaLiikennemuodot(
        hhs: Haittojenhallintasuunnitelma,
        tt: TormaystarkasteluTulos,
        path: String
    ): ValidationResult =
        validate()
            .andWhen(tt.autoliikenne.indeksi > 0) {
                notNullOrBlank(hhs[Haittojenhallintatyyppi.AUTOLIIKENNE], "$path.AUTOLIIKENNE")
            }
            .andWhen(tt.pyoraliikenneindeksi > 0) {
                notNullOrBlank(hhs[Haittojenhallintatyyppi.PYORALIIKENNE], "$path.PYORALIIKENNE")
            }
            .andWhen(tt.linjaautoliikenneindeksi > 0) {
                notNullOrBlank(
                    hhs[Haittojenhallintatyyppi.LINJAAUTOLIIKENNE], "$path.LINJAAUTOLIIKENNE")
            }
            .andWhen(tt.raitioliikenneindeksi > 0f) {
                notNullOrBlank(hhs[Haittojenhallintatyyppi.RAITIOLIIKENNE], "$path.RAITIOLIIKENNE")
            }
}

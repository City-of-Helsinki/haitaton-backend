package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.isValidBusinessId
import fi.hel.haitaton.hanke.isValidOVT
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.ValidationResult.Companion.allIn
import fi.hel.haitaton.hanke.validation.Validators.isBeforeOrEqual
import fi.hel.haitaton.hanke.validation.Validators.notBlank
import fi.hel.haitaton.hanke.validation.Validators.notEmpty
import fi.hel.haitaton.hanke.validation.Validators.notJustWhitespace
import fi.hel.haitaton.hanke.validation.Validators.notNull
import fi.hel.haitaton.hanke.validation.Validators.notNullOrBlank
import fi.hel.haitaton.hanke.validation.Validators.notNullOrEmpty
import fi.hel.haitaton.hanke.validation.Validators.validate
import fi.hel.haitaton.hanke.validation.Validators.validateTrue
import java.util.regex.Pattern.CASE_INSENSITIVE

val johtoselvitystunnusPattern = "^JS\\d{7}$".toPattern(CASE_INSENSITIVE).toRegex()

/**
 * Validate required field are set. When application is a draft, it is ok to have fields that are
 * not yet defined. But e.g. when sending, they must be present.
 */
fun KaivuilmoitusData.validateForSend(): ValidationResult =
    validate { notBlank(name, "name") }
        .and { notBlank(workDescription, "workDescription") }
        .and { validateWorkInvolves() }
        .andWhen(!cableReportDone) { notNull(rockExcavation, "rockExcavation") }
        .andWhen(cableReportDone) { notNullOrEmpty(cableReports, "cableReports") }
        .andWhen(!cableReports.isNullOrEmpty()) {
            allIn(cableReports!!, "cableReports") { c, p ->
                validateTrue(c.matches(johtoselvitystunnusPattern), p)
            }
        }
        .and { validateTrue(requiredCompetence, "requiredCompetence") }
        .and { notNull(startTime, "startTime") }
        .and { notNull(endTime, "endTime") }
        .andWhen(startTime != null && endTime != null) {
            isBeforeOrEqual(startTime!!, endTime!!, "endTime")
        }
        .andWithNotNull(areas, "areas", ::validateAreas)
        .andWithNotNull(customerWithContacts, "customerWithContacts") {
            validate(it)
                .and { validateTrue(isRegistryKeyValid(tyyppi, registryKey), "$it.registryKey") }
                .and { notEmpty(yhteyshenkilot, "$it.yhteyshenkilot") }
        }
        .andWithNotNull(contractorWithContacts, "contractorWithContacts") {
            validate(it).and { notEmpty(yhteyshenkilot, "$it.yhteyshenkilot") }
        }
        .whenNotNull(representativeWithContacts) { it.validate("representativeWithContacts") }
        .whenNotNull(propertyDeveloperWithContacts) { it.validate("propertyDeveloperWithContacts") }
        .andWithNotNull(invoicingCustomer, "invoicingCustomer") { validate(it) }
        .whenNotNull(additionalInfo) { notJustWhitespace(it, "additionalInfo") }

internal fun validateAreas(areas: List<KaivuilmoitusAlue>, path: String) =
    validate { notEmpty(areas, path) }.and { allIn(areas, path, ::validateArea) }

internal fun validateArea(area: KaivuilmoitusAlue, path: String): ValidationResult =
    area.validate(path)

private fun KaivuilmoitusAlue.validate(path: String): ValidationResult =
    validate { notEmpty(tyoalueet, "$path.tyoalueet") }
        .and { notBlank(katuosoite, "$path.katuosoite") }
        .and { notEmpty(tyonTarkoitukset, "$path.tyonTarkoitukset") }

private fun KaivuilmoitusData.validateWorkInvolves(): ValidationResult =
    validate { validateTrue(constructionWork, "constructionWork") }
        .or { validateTrue(maintenanceWork, "maintenanceWork") }
        .or { validateTrue(emergencyWork, "emergencyWork") }

internal fun Hakemusyhteystieto.validate(path: String) =
    validate { notBlank(nimi, "$path.nimi") }
        .and { notBlank(sahkoposti, "$path.sahkoposti") }
        .and { notBlank(puhelinnumero, "$path.puhelinnumero") }
        .whenNotNull(registryKey) {
            validateTrue(registryKey.isValidBusinessId(), "$path.registryKey")
        }
        .andAllIn(yhteyshenkilot, "$path.yhteyshenkilot", ::validateFullInfo)

private fun isRegistryKeyValid(tyyppi: CustomerType, ytunnus: String?): Boolean =
    when (tyyppi) {
        CustomerType.COMPANY -> ytunnus != null
        CustomerType.ASSOCIATION -> ytunnus != null
        CustomerType.PERSON -> true // TODO: Validate for personal identity code
        CustomerType.OTHER -> true // TODO: ???
    }

private fun validateFullInfo(yhteyshenkilo: Hakemusyhteyshenkilo, path: String) =
    validate { notBlank(yhteyshenkilo.etunimi, "$path.etunimi") }
        .and { notBlank(yhteyshenkilo.sukunimi, "$path.sukunimi") }
        .and { notBlank(yhteyshenkilo.sahkoposti, "$path.sahkoposti") }
        .and { notBlank(yhteyshenkilo.puhelin, "$path.puhelin") }

internal fun Laskutusyhteystieto.validate(path: String): ValidationResult =
    validate { notBlank(nimi, "$path.nimi") }
        .and { notJustWhitespace(sahkoposti, "$path.sahkoposti") }
        .and { notJustWhitespace(puhelinnumero, "$path.puhelinnumero") }
        .whenNotNull(registryKey) {
            validateTrue(registryKey.isValidBusinessId(), "$path.registryKey")
        }
        .and { validateTrue(isRegistryKeyValid(tyyppi, registryKey), "$path.registryKey") }
        .whenNotNull(ovttunnus) { validateTrue(ovttunnus.isValidOVT(), "$path.ovttunnus") }
        .and {
            validate {
                    notNull(ovttunnus, "$path.ovttunnus").and {
                        notNullOrBlank(valittajanTunnus, "$path.valittajanTunnus")
                    }
                }
                .or {
                    notNullOrBlank(katuosoite, "$path.katuosoite")
                        .and { notNullOrBlank(postinumero, "$path.postinumero") }
                        .and { notNullOrBlank(postitoimipaikka, "$path.postitoimipaikka") }
                }
        }

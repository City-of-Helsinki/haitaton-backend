package fi.hel.haitaton.hanke.hakemus

import assertk.assertThat
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.DEFAULT_OVT
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory.withYhteyshenkilo
import fi.hel.haitaton.hanke.test.Asserts.failsWith
import fi.hel.haitaton.hanke.test.Asserts.succeeds
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EmptySource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

class KaivuilmoitusSendValidationTest {

    @Test
    fun `succeeds when kaivuilmoitus has all good data`() {
        assertThat(hakemus.validateForSend()).succeeds()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", BLANK])
    fun `fails when name is empty or blank`(name: String) {
        val hakemus = hakemus.copy(name = name)

        assertThat(hakemus.validateForSend()).failsWith("name")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", BLANK])
    fun `fails when workDescription is empty or blank`(workDescription: String) {
        val hakemus = hakemus.copy(workDescription = workDescription)

        assertThat(hakemus.validateForSend()).failsWith("workDescription")
    }

    @Test
    fun `fails when nothing has been selected for work involves`() {
        val hakemus =
            hakemus.copy(
                constructionWork = false,
                maintenanceWork = false,
                emergencyWork = false,
            )

        assertThat(hakemus.validateForSend())
            .failsWith("constructionWork", "maintenanceWork", "emergencyWork")
    }

    @ParameterizedTest
    @CsvSource("true,false,false", "false,true,false", "false,false,true")
    fun `succeeds when any of the work involves have been selected`(
        constructionWork: Boolean,
        maintenanceWork: Boolean,
        emergencyWork: Boolean,
    ) {
        val hakemus =
            hakemus.copy(
                constructionWork = constructionWork,
                maintenanceWork = maintenanceWork,
                emergencyWork = emergencyWork,
            )

        val result = hakemus.validateForSend()

        assertThat(result.isOk()).isTrue()
    }

    @Test
    fun `fails when requesting a new cable report but rock excavation not specified`() {
        val hakemus = hakemus.copy(cableReportDone = false, rockExcavation = null)

        assertThat(hakemus.validateForSend()).failsWith("rockExcavation")
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    fun `fails when not requesting a new cable report but no cable reports given`(
        cableReports: List<String>?
    ) {
        val hakemus = hakemus.copy(cableReportDone = true, cableReports = cableReports)

        assertThat(hakemus.validateForSend()).failsWith("cableReports")
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `fails when cable reports has an invalid value`(cableReportDone: Boolean) {
        val hakemus =
            hakemus.copy(
                cableReportDone = cableReportDone,
                cableReports = listOf("JS2400001", "bad"),
            )

        assertThat(hakemus.validateForSend()).failsWith("cableReports[1]")
    }

    @Test
    fun `fails when required competence is not selected`() {
        val hakemus = hakemus.copy(requiredCompetence = false)

        assertThat(hakemus.validateForSend()).failsWith("requiredCompetence")
    }

    @Test
    fun `fails when start time is not selected`() {
        val hakemus = hakemus.copy(startTime = null)

        assertThat(hakemus.validateForSend()).failsWith("startTime")
    }

    @Test
    fun `fails when end time is not selected`() {
        val hakemus = hakemus.copy(endTime = null)

        assertThat(hakemus.validateForSend()).failsWith("endTime")
    }

    @Test
    fun `fails when end time is before start time`() {
        val hakemus =
            hakemus.copy(
                startTime = DateFactory.getEndDatetime(),
                endTime = DateFactory.getStartDatetime(),
            )

        assertThat(hakemus.validateForSend()).failsWith("endTime")
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    fun `fails when areas are null or empty`(areas: List<KaivuilmoitusAlue>?) {
        val hakemus = hakemus.copy(areas = areas)

        assertThat(hakemus.validateForSend()).failsWith("areas")
    }

    @Test
    fun `fails when there is no customer`() {
        val hakemus = hakemus.copy(customerWithContacts = null)

        assertThat(hakemus.validateForSend()).failsWith("customerWithContacts")
    }

    @Test
    fun `fails when there is no contractor`() {
        val hakemus = hakemus.copy(contractorWithContacts = null)

        assertThat(hakemus.validateForSend()).failsWith("contractorWithContacts")
    }

    @Test
    fun `succeeds when there is no property developer`() {
        val hakemus = hakemus.copy(propertyDeveloperWithContacts = null)

        assertThat(hakemus.validateForSend()).succeeds()
    }

    @Test
    fun `succeeds when there is no representative`() {
        val hakemus = hakemus.copy(representativeWithContacts = null)

        assertThat(hakemus.validateForSend()).succeeds()
    }

    @ParameterizedTest
    @EnumSource(value = CustomerType::class, names = ["COMPANY", "ASSOCIATION"])
    fun `fails when the company customer has no registry key`(tyyppi: CustomerType) {
        val customer =
            HakemusyhteystietoFactory.create(tyyppi = tyyppi, ytunnus = null).withYhteyshenkilo()
        val hakemus = hakemus.copy(customerWithContacts = customer)

        assertThat(hakemus.validateForSend()).failsWith("customerWithContacts.ytunnus")
    }

    @ParameterizedTest
    @EnumSource(value = CustomerType::class, names = ["PERSON", "OTHER"])
    fun `succeeds when the person or other customer has no registry key`(tyyppi: CustomerType) {
        val customer =
            HakemusyhteystietoFactory.create(tyyppi = tyyppi, ytunnus = null).withYhteyshenkilo()
        val hakemus = hakemus.copy(customerWithContacts = customer)

        assertThat(hakemus.validateForSend()).succeeds()
    }

    @ParameterizedTest
    @EnumSource(value = CustomerType::class, names = ["COMPANY", "ASSOCIATION"])
    fun `succeeds when the company contractor has no registry key`(tyyppi: CustomerType) {
        val customer =
            HakemusyhteystietoFactory.create(tyyppi = tyyppi, ytunnus = null).withYhteyshenkilo()
        val hakemus = hakemus.copy(contractorWithContacts = customer)

        assertThat(hakemus.validateForSend()).succeeds()
    }

    @ParameterizedTest
    @EnumSource(value = CustomerType::class)
    fun `fails when the customer has a bad registry key`(tyyppi: CustomerType) {
        val customer =
            HakemusyhteystietoFactory.create(tyyppi = tyyppi, ytunnus = "bad").withYhteyshenkilo()
        val hakemus = hakemus.copy(customerWithContacts = customer)

        assertThat(hakemus.validateForSend()).failsWith("customerWithContacts.ytunnus")
    }

    @Test
    fun `fails when the customer has no yhteyshenkilot`() {
        val customer = HakemusyhteystietoFactory.create()
        val hakemus = hakemus.copy(customerWithContacts = customer)

        assertThat(hakemus.validateForSend()).failsWith("customerWithContacts.yhteyshenkilot")
    }

    @Test
    fun `fails when the contractor has no yhteyshenkilot`() {
        val customer = HakemusyhteystietoFactory.create()
        val hakemus = hakemus.copy(contractorWithContacts = customer)

        assertThat(hakemus.validateForSend()).failsWith("contractorWithContacts.yhteyshenkilot")
    }

    @Test
    fun `succeeds when the property developer has no yhteyshenkilot`() {
        val customer = HakemusyhteystietoFactory.create()
        val hakemus = hakemus.copy(propertyDeveloperWithContacts = customer)

        assertThat(hakemus.validateForSend()).succeeds()
    }

    @Test
    fun `succeeds when the representative has no yhteyshenkilot`() {
        val customer = HakemusyhteystietoFactory.create()
        val hakemus = hakemus.copy(representativeWithContacts = customer)

        assertThat(hakemus.validateForSend()).succeeds()
    }

    @Test
    fun `fails when there is no invoicing customer`() {
        val hakemus = hakemus.copy(invoicingCustomer = null)

        assertThat(hakemus.validateForSend()).failsWith("invoicingCustomer")
    }

    @Test
    fun `fails when invoicing customer has an error`() {
        val invoicingCustomer = HakemusyhteystietoFactory.createLaskutusyhteystieto(nimi = "")
        val hakemus = hakemus.copy(invoicingCustomer = invoicingCustomer)

        assertThat(hakemus.validateForSend()).failsWith("invoicingCustomer.nimi")
    }

    @Test
    fun `fails when additional info is just blanks`() {
        val hakemus = hakemus.copy(additionalInfo = BLANK)

        assertThat(hakemus.validateForSend()).failsWith("additionalInfo")
    }

    @Test
    fun `succeeds when additional info is empty`() {
        val hakemus = hakemus.copy(additionalInfo = "")

        assertThat(hakemus.validateForSend()).succeeds()
    }

    @Nested
    inner class ValidateAreas {
        @Test
        fun `succeeds when there are valid areas`() {
            val areas =
                listOf(
                    ApplicationFactory.createExcavationNotificationArea(),
                    ApplicationFactory.createExcavationNotificationArea(),
                )

            assertThat(validateAreas(areas, "areas")).succeeds()
        }

        @Test
        fun `fails when areas is empty`() {
            val areas = listOf<KaivuilmoitusAlue>()

            assertThat(validateAreas(areas, "areas")).failsWith("areas")
        }

        @Test
        fun `fails when one of the areas has an error`() {
            val areas =
                listOf(
                    ApplicationFactory.createExcavationNotificationArea(),
                    ApplicationFactory.createExcavationNotificationArea(tyoalueet = listOf()),
                    ApplicationFactory.createExcavationNotificationArea(),
                )

            assertThat(validateAreas(areas, "areas")).failsWith("areas[1].tyoalueet")
        }
    }

    @Nested
    inner class ValidateArea {
        @Test
        fun `succeeds when the area has all necessary info`() {
            val area = ApplicationFactory.createExcavationNotificationArea()

            assertThat(validateArea(area, "area")).succeeds()
        }

        @Test
        fun `fails when the area has no tyoalueet`() {
            val area = ApplicationFactory.createExcavationNotificationArea(tyoalueet = listOf())

            assertThat(validateArea(area, "area")).failsWith("area.tyoalueet")
        }

        @ParameterizedTest
        @ValueSource(strings = ["", BLANK])
        fun `fails when the area has blank or empty katuosoite`(osoite: String) {
            val area = ApplicationFactory.createExcavationNotificationArea(katuosoite = osoite)

            assertThat(validateArea(area, "area")).failsWith("area.katuosoite")
        }

        @Test
        fun `fails when the area has no tyon tarkoitukset`() {
            val area =
                ApplicationFactory.createExcavationNotificationArea(tyonTarkoitukset = setOf())

            assertThat(validateArea(area, "area")).failsWith("area.tyonTarkoitukset")
        }
    }

    @Nested
    inner class ValidateHakemusyhteystieto {
        @Test
        fun `succeeds when the yhteystieto has all necessary info`() {
            val yhteystieto = HakemusyhteystietoFactory.create()

            assertThat(yhteystieto.validate("customer")).succeeds()
        }

        @ParameterizedTest
        @ValueSource(strings = ["", BLANK])
        fun `fails when nimi is empty or blank`(nimi: String) {
            val yhteystieto = HakemusyhteystietoFactory.create(nimi = nimi)

            assertThat(yhteystieto.validate("customer")).failsWith("customer.nimi")
        }

        @ParameterizedTest
        @ValueSource(strings = ["", BLANK])
        fun `fails when sahkoposti is empty or blank`(sahkoposti: String) {
            val yhteystieto = HakemusyhteystietoFactory.create(sahkoposti = sahkoposti)

            assertThat(yhteystieto.validate("customer")).failsWith("customer.sahkoposti")
        }

        @ParameterizedTest
        @ValueSource(strings = ["", BLANK])
        fun `fails when puhelinnumero is empty or blank`(puhelinnumero: String) {
            val yhteystieto = HakemusyhteystietoFactory.create(puhelinnumero = puhelinnumero)

            assertThat(yhteystieto.validate("customer")).failsWith("customer.puhelinnumero")
        }

        @ParameterizedTest
        @ValueSource(strings = ["", BLANK, "bad"])
        fun `fails when ytunnus is invalid`(ytunnus: String) {
            val yhteystieto = HakemusyhteystietoFactory.create(ytunnus = ytunnus)

            assertThat(yhteystieto.validate("customer")).failsWith("customer.ytunnus")
        }

        @Test
        fun `succeeds when ytunnus is null`() {
            val yhteystieto = HakemusyhteystietoFactory.create(ytunnus = null)

            assertThat(yhteystieto.validate("customer")).succeeds()
        }

        @Test
        fun `succeeds when yhteyshenkilot is empty`() {
            val yhteystieto = HakemusyhteystietoFactory.create(yhteyshenkilot = listOf())

            assertThat(yhteystieto.validate("customer")).succeeds()
        }

        @ParameterizedTest
        @ValueSource(strings = ["", BLANK])
        fun `fails when yhteyshenkilo etunimi is empty or blank`(etunimi: String) {
            val yhteystieto =
                HakemusyhteystietoFactory.create()
                    .withYhteyshenkilo()
                    .withYhteyshenkilo(etunimi = etunimi)

            assertThat(yhteystieto.validate("customer"))
                .failsWith("customer.yhteyshenkilot[1].etunimi")
        }

        @ParameterizedTest
        @ValueSource(strings = ["", BLANK])
        fun `fails when yhteyshenkilo sukunimi is empty or blank`(sukunimi: String) {
            val yhteystieto =
                HakemusyhteystietoFactory.create()
                    .withYhteyshenkilo()
                    .withYhteyshenkilo(sukunimi = sukunimi)

            assertThat(yhteystieto.validate("customer"))
                .failsWith("customer.yhteyshenkilot[1].sukunimi")
        }

        @ParameterizedTest
        @ValueSource(strings = ["", BLANK])
        fun `fails when yhteyshenkilo sahkoposti is empty or blank`(sahkoposti: String) {
            val yhteystieto =
                HakemusyhteystietoFactory.create()
                    .withYhteyshenkilo(sahkoposti = sahkoposti)
                    .withYhteyshenkilo()

            assertThat(yhteystieto.validate("customer"))
                .failsWith("customer.yhteyshenkilot[0].sahkoposti")
        }

        @ParameterizedTest
        @ValueSource(strings = ["", BLANK])
        fun `fails when yhteyshenkilo puhelin is empty or blank`(puhelin: String) {
            val yhteystieto =
                HakemusyhteystietoFactory.create()
                    .withYhteyshenkilo()
                    .withYhteyshenkilo(puhelin = puhelin)

            assertThat(yhteystieto.validate("customer"))
                .failsWith("customer.yhteyshenkilot[1].puhelin")
        }
    }

    @Nested
    inner class ValidateLaskutusyhteystieto {
        @Test
        fun `succeeds when invoicing customer has all necessary info`() {
            val invoicingCustomer = HakemusyhteystietoFactory.createLaskutusyhteystieto()

            assertThat(invoicingCustomer.validate("invoicingCustomer")).succeeds()
        }

        @ParameterizedTest
        @ValueSource(strings = ["", BLANK])
        fun `fails when invoicing customer has a blank or empty nimi`(nimi: String) {
            val invoicingCustomer = HakemusyhteystietoFactory.createLaskutusyhteystieto(nimi = nimi)

            assertThat(invoicingCustomer.validate("invoicingCustomer"))
                .failsWith("invoicingCustomer.nimi")
        }

        @Test
        fun `fails when invoicing customer has a blank sahkoposti`() {
            val invoicingCustomer =
                HakemusyhteystietoFactory.createLaskutusyhteystieto(sahkoposti = BLANK)

            assertThat(invoicingCustomer.validate("invoicingCustomer"))
                .failsWith("invoicingCustomer.sahkoposti")
        }

        @Test
        fun `fails when invoicing customer has a blank puhelinnumero`() {
            val invoicingCustomer =
                HakemusyhteystietoFactory.createLaskutusyhteystieto(puhelinnumero = BLANK)

            assertThat(invoicingCustomer.validate("invoicingCustomer"))
                .failsWith("invoicingCustomer.puhelinnumero")
        }

        @ParameterizedTest
        @ValueSource(strings = ["", BLANK, "bad"])
        fun `fails when invoicing customer has a bad ytunnus`(ytunnus: String) {
            val invoicingCustomer =
                HakemusyhteystietoFactory.createLaskutusyhteystieto(ytunnus = ytunnus)

            assertThat(invoicingCustomer.validate("invoicingCustomer"))
                .failsWith("invoicingCustomer.ytunnus")
        }

        @Test
        fun `succeeds when person invoicing customer has no ytunnus`() {
            val invoicingCustomer =
                HakemusyhteystietoFactory.createLaskutusyhteystieto(
                    tyyppi = CustomerType.PERSON,
                    ytunnus = null
                )

            assertThat(invoicingCustomer.validate("invoicingCustomer")).succeeds()
        }

        @Test
        fun `fails when company invoicing customer has no ytunnus`() {
            val invoicingCustomer =
                HakemusyhteystietoFactory.createLaskutusyhteystieto(
                    tyyppi = CustomerType.COMPANY,
                    ytunnus = null
                )

            assertThat(invoicingCustomer.validate("invoicingCustomer"))
                .failsWith("invoicingCustomer.ytunnus")
        }

        @ParameterizedTest
        @ValueSource(strings = ["", BLANK, "bad"])
        fun `fails when invoicing customer has a bad OVT`(ovt: String) {
            val invoicingCustomer =
                HakemusyhteystietoFactory.createLaskutusyhteystieto(ovttunnus = ovt)

            assertThat(invoicingCustomer.validate("invoicingCustomer"))
                .failsWith("invoicingCustomer.ovttunnus")
        }

        @Test
        fun `fails when invoicing customer has neither a full address nor billing information`() {
            val invoicingCustomer =
                HakemusyhteystietoFactory.createLaskutusyhteystieto(
                    ovttunnus = null,
                    valittajanTunnus = null,
                    katuosoite = null,
                    postinumero = null,
                    postitoimipaikka = null,
                )

            assertThat(invoicingCustomer.validate("invoicingCustomer"))
                .failsWith(
                    "invoicingCustomer.ovttunnus",
                    "invoicingCustomer.valittajanTunnus",
                    "invoicingCustomer.katuosoite",
                    "invoicingCustomer.postinumero",
                    "invoicingCustomer.postitoimipaikka",
                )
        }

        @Test
        fun `fails when invoicing customer has only partial address and billing information`() {
            val invoicingCustomer =
                HakemusyhteystietoFactory.createLaskutusyhteystieto(
                    ovttunnus = null,
                    valittajanTunnus = DEFAULT_OVT,
                    katuosoite = null,
                    postinumero = "00890",
                    postitoimipaikka = null,
                )

            assertThat(invoicingCustomer.validate("invoicingCustomer"))
                .failsWith(
                    "invoicingCustomer.ovttunnus",
                    "invoicingCustomer.katuosoite",
                    "invoicingCustomer.postitoimipaikka",
                )
        }

        @Test
        fun `succeeds when invoicing customer has full address and no billing information`() {
            val invoicingCustomer =
                HakemusyhteystietoFactory.createLaskutusyhteystieto(
                    ovttunnus = null,
                    valittajanTunnus = null,
                    katuosoite = "Katu 1",
                    postinumero = "00890",
                    postitoimipaikka = "Helsinki",
                )

            assertThat(invoicingCustomer.validate("invoicingCustomer")).succeeds()
        }

        @Test
        fun `succeeds when invoicing customer has full billing information and no address`() {
            val invoicingCustomer =
                HakemusyhteystietoFactory.createLaskutusyhteystieto(
                    ovttunnus = DEFAULT_OVT,
                    valittajanTunnus = DEFAULT_OVT,
                    katuosoite = null,
                    postinumero = null,
                    postitoimipaikka = null,
                )

            assertThat(invoicingCustomer.validate("invoicingCustomer")).succeeds()
        }
    }

    companion object {
        private const val BLANK = "   \t\n\t   "

        val hakemus =
            HakemusFactory.createKaivuilmoitusData(
                constructionWork = true,
                requiredCompetence = true,
                cableReportDone = false,
                rockExcavation = false,
                customerWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                contractorWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                invoicingCustomer = HakemusyhteystietoFactory.createLaskutusyhteystieto()
            )
    }
}

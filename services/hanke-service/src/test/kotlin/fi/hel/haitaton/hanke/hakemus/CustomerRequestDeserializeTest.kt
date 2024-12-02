package fi.hel.haitaton.hanke.hakemus

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasClass
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.parseJson
import fi.hel.haitaton.hanke.toJsonString
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CustomerRequestDeserializeTest {

    @Nested
    inner class RegistryKeyHidden {
        @Test
        fun `is false when null in JSON`() {
            val customer = HakemusUpdateRequestFactory.createCustomer()
            val json: ObjectNode = OBJECT_MAPPER.valueToTree(customer)
            json.putNull("registryKeyHidden")
            val jsonString = json.toString()
            assertThat(jsonString).contains("\"registryKeyHidden\":null")

            val result: CustomerRequest = jsonString.parseJson()

            assertThat(result.registryKeyHidden).isFalse()
        }

        @Test
        fun `is false when missing from JSON`() {
            val customer = HakemusUpdateRequestFactory.createCustomer()
            val json: ObjectNode = OBJECT_MAPPER.valueToTree(customer)
            json.remove("registryKeyHidden")
            val jsonString = json.toString()
            assertThat(jsonString).doesNotContain("registryKeyHidden")

            val result: CustomerRequest = jsonString.parseJson()

            assertThat(result.registryKeyHidden).isFalse()
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `uses value when value is proper boolean in JSON`(value: Boolean) {
            val customer = HakemusUpdateRequestFactory.createCustomer(registryKeyHidden = value)
            val jsonString = customer.toJsonString()
            assertThat(jsonString).contains("\"registryKeyHidden\":$value")

            val result: CustomerRequest = jsonString.parseJson()

            assertThat(result.registryKeyHidden).isEqualTo(value)
        }

        @Test
        fun `throws exception when value is nonsense in JSON`() {
            val customer = HakemusUpdateRequestFactory.createCustomer()
            val json: ObjectNode = OBJECT_MAPPER.valueToTree(customer)
            json.put("registryKeyHidden", "nonsense")
            val jsonString = json.toString()
            assertThat(jsonString).contains("\"registryKeyHidden\":\"nonsense\"")

            assertFailure { jsonString.parseJson() }.hasClass(UnrecognizedPropertyException::class)
        }
    }
}

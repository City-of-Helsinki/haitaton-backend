package fi.hel.haitaton.hanke.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.util.stream.Stream
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

private const val DUMMY_EMAIL = "dummymail@mail.com"
private const val DUMMY_PHONE = "04012345678"

class ContactTest {

    @ParameterizedTest
    @CsvSource(
        "Matti,Meikalainen,Matti Meikalainen",
        "'',Meikalainen,Meikalainen",
        "Matti,'',Matti",
        "'','',''"
    )
    fun `fullName concatenates first and last names`(
        firstName: String,
        lastName: String,
        expectedResult: String
    ) {
        val contact = Contact(firstName, lastName, DUMMY_EMAIL, DUMMY_PHONE)
        assertThat(contact.fullName()).isEqualTo(expectedResult)
    }

    @Test
    fun `fullName when firstName null should provide lastName`() {
        val contact =
            Contact(firstName = null, lastName = "Last", email = DUMMY_EMAIL, phone = DUMMY_PHONE)
        assertThat(contact.fullName()).isEqualTo("Last")
    }

    @Test
    fun `fullName when lastName null should provide firstName`() {
        val contact =
            Contact(firstName = "First", lastName = null, email = DUMMY_EMAIL, phone = DUMMY_PHONE)
        assertThat(contact.fullName()).isEqualTo("First")
    }

    @Test
    fun `fullName when both names null should provide null`() {
        val contact =
            Contact(firstName = null, lastName = null, email = DUMMY_EMAIL, phone = DUMMY_PHONE)
        assertThat(contact.fullName()).isNull()
    }

    @ParameterizedTest
    @MethodSource("invalidHankeKayttajaContacts")
    fun `toHankeKayttajaInput when missing data should return null`(contact: Contact) {
        val result = contact.toHankekayttajaInput()

        assertThat(result).isNull()
    }

    companion object {

        @JvmStatic
        fun invalidHankeKayttajaContacts(): Stream<Contact> =
            listOf(
                    CONTACT.copy(firstName = null),
                    CONTACT.copy(firstName = ""),
                    CONTACT.copy(lastName = null),
                    CONTACT.copy(lastName = ""),
                    CONTACT.copy(email = null),
                    CONTACT.copy(email = ""),
                    CONTACT.copy(phone = ""),
                    CONTACT.copy(phone = null)
                )
                .stream()

        private val CONTACT =
            Contact(
                firstName = "Firstname",
                lastName = "Lastname",
                email = "test@email.com",
                phone = "04012345678",
                orderer = true,
            )
    }
}

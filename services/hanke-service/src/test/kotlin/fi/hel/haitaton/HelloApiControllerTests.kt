package fi.hel.haitaton

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/**
 * Unit test for HelloApiController.
 *
 * (Quite simple to test, as the only method does not need any input
 * and does not use any other classes, objects or services (nothing to mock).
 * Normally this type of unit testing would be aimed at a java/kotlin level
 * service class, not at the REST controller class, but in this simple case,
 * both functionalities are in the same class.)
 *
 * Lifecycle.PER_CLASS used for easier testing of the counter.
 * (And to provide an example of this non-default mode.)
 * See https://junit.org/junit5/docs/current/user-guide/#writing-tests-test-instance-lifecycle
 */
@TestInstance(Lifecycle.PER_CLASS)
class HelloApiControllerTests {

    private val controllerUnderTest = HelloApiController()

    // Nothing to do in any of these setup/teardown methods, but left here as examples
    @BeforeAll
    fun allSetup() {
    }

    @BeforeEach
    fun eachSetup() {
    }

    @AfterEach
    fun eachTeardown() {
    }

    @AfterAll
    fun allTeardown() {
    }

    @Test
    fun `Test that the two first responses are as expected`() {
        var response : ResponseEntity<Any>
        var responsePayload : HelloResponse

        // First call:
        response = controllerUnderTest.hello()
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull
        assertThat(response.body).isExactlyInstanceOf(Class.forName("fi.hel.haitaton.HelloResponse"))
        responsePayload = response.body as HelloResponse
        assertThat(responsePayload.count).isEqualTo(1)
        assertThat(responsePayload.message).contains("Hello")

        // Second call - counter works, message is different:
        response = controllerUnderTest.hello()
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull
        assertThat(response.body).isExactlyInstanceOf(Class.forName("fi.hel.haitaton.HelloResponse"))
        responsePayload = response.body as HelloResponse
        assertThat(responsePayload.count).isEqualTo(2)
        assertThat(responsePayload.message).contains("again")
    }

}
package fi.hel.haitaton.hanke.email

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.startsWith
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.slot
import io.mockk.spyk
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.apache.commons.text.StringEscapeUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mail.javamail.JavaMailSender

private const val INVITER_NAME = "Matti Meikäläinen"
private const val INVITER_EMAIL = "matti.meikalainen@test.fi"
private const val TEST_EMAIL = "test@test.test"
private const val HANKE_TUNNUS = "HAI24-1"
private const val HANKE_NIMI = "Mannerheimintien liikenneuudistus"
private const val APPLICATION_IDENTIFIER = "JS2300001"

class EmailSenderServiceTest {

    private val emailConfig =
        EmailProperties(
            from = "haitaton@hel.fi",
            baseUrl = "https://haitaton.hel.fi",
            filter = EmailFilterProperties(false, listOf())
        )
    private val featureFlags = FeatureFlags(mapOf(Feature.USER_MANAGEMENT to true))
    private val mailSender: JavaMailSender = spyk()
    private val emailSenderService = EmailSenderService(mailSender, emailConfig, featureFlags)

    @BeforeEach
    fun clean() {
        clearAllMocks()
        every { mailSender.createMimeMessage() } returns MimeMessage(null as Session?)
    }

    @Nested
    inner class HankeInvitation {
        private val invitationToken = "MgtzRbcPsvoKQamnaSxCnmW7"
        private val hankeInvitation =
            HankeInvitationData(
                inviterName = INVITER_NAME,
                inviterEmail = INVITER_EMAIL,
                recipientEmail = TEST_EMAIL,
                hankeTunnus = HANKE_TUNNUS,
                hankeNimi = HANKE_NIMI,
                invitationToken = invitationToken,
            )

        private fun sendAndCapture(): MimeMessage {
            val email = slot<MimeMessage>()
            justRun { mailSender.send(capture(email)) }

            emailSenderService.sendHankeInvitationEmail(hankeInvitation)

            return email.captured
        }

        @Test
        fun `has the correct subject`() {
            val email = sendAndCapture()

            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Sinut on lisätty hankkeelle HAI24-1 " +
                        "/ Du har lagts till i projektet HAI24-1 " +
                        "/ You have been added to project HAI24-1"
                )
        }

        @Test
        fun `has a header line in the body`() {
            val (textBody, htmlBody) = sendAndCapture().bodies()

            assertThat(textBody)
                .contains(
                    "Sinut on lisätty hankkeelle HAI24-1 " +
                        "/ Du har lagts till i projektet HAI24-1 " +
                        "/ You have been added to project HAI24-1"
                )
            assertThat(htmlBody)
                .containsEscaped(
                    "Sinut on lisätty hankkeelle HAI24-1 " +
                        "/ Du har lagts till i projektet HAI24-1 " +
                        "/ You have been added to project HAI24-1"
                )
        }

        @Nested
        inner class BodyInFinnish {
            @Test
            fun `contains the invitation url`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody)
                    .contains("https://haitaton.hel.fi/fi/kutsu?id=$invitationToken")
                assertThat(htmlBody)
                    .contains("""<a href="https://haitaton.hel.fi/fi/kutsu?id=$invitationToken">""")
            }

            @Test
            fun `contains the signature`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).all {
                    containsLine("Tämä on automaattinen sähköposti – älä vastaa tähän viestiin.")
                    containsLine("Ystävällisin terveisin,")
                    containsLine("Helsingin kaupungin kaupunkiympäristön toimiala")
                    containsLine("Haitaton-asiointi")
                    containsLine("haitaton@hel.fi")
                }
                assertThat(htmlBody).all {
                    containsEscaped("Tämä on automaattinen sähköposti – älä vastaa tähän viestiin.")
                    containsEscaped("Ystävällisin terveisin,")
                    containsEscaped("Helsingin kaupungin kaupunkiympäristön toimiala")
                    containsEscaped("Haitaton-asiointi")
                    containsEscaped("haitaton@hel.fi")
                }
            }

            @Test
            fun `contains the inviter information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).all {
                    contains("$INVITER_NAME ($INVITER_EMAIL) lisäsi sinut hankkeelle")
                }
                assertThat(htmlBody).all {
                    containsEscaped("$INVITER_NAME ($INVITER_EMAIL) lisäsi sinut hankkeelle")
                }
            }

            @Test
            fun `contains the hanke information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).all { contains("hankkeelle $HANKE_NIMI ($HANKE_TUNNUS).") }
                assertThat(htmlBody).all {
                    contains("hankkeelle <b>$HANKE_NIMI ($HANKE_TUNNUS)</b>.")
                }
            }
        }

        @Nested
        inner class InSwedish {
            @Test
            fun `contains the invitation url`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody)
                    .contains("https://haitaton.hel.fi/sv/inbjudan?id=$invitationToken")
                assertThat(htmlBody)
                    .contains(
                        """<a href="https://haitaton.hel.fi/sv/inbjudan?id=$invitationToken">"""
                    )
            }

            @Test
            fun `contains the signature`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).all {
                    containsLine("Det här är ett automatiskt e-postmeddelande – svara inte på det.")
                    containsLine("Med vänlig hälsning,")
                    containsLine("Helsingfors stads stadsmiljösektor")
                    containsLine("Haitaton-ärenden")
                    containsLine("haitaton@hel.fi")
                }
                assertThat(htmlBody).all {
                    containsEscaped(
                        "Det här är ett automatiskt e-postmeddelande – svara inte på det."
                    )
                    containsEscaped("Med vänlig hälsning,")
                    containsEscaped("Helsingfors stads stadsmiljösektor")
                    containsEscaped("Haitaton-ärenden")
                    containsEscaped("haitaton@hel.fi")
                }
            }

            @Test
            fun `contains the inviter information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).all {
                    contains("$INVITER_NAME ($INVITER_EMAIL) lade till dig i projektet")
                }
                assertThat(htmlBody).all {
                    containsEscaped("$INVITER_NAME ($INVITER_EMAIL) lade till dig i projektet")
                }
            }

            @Test
            fun `contains the hanke information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).all { contains("i projektet $HANKE_NIMI ($HANKE_TUNNUS).") }
                assertThat(htmlBody).all {
                    contains("i projektet <b>$HANKE_NIMI ($HANKE_TUNNUS)</b>.")
                }
            }
        }

        @Nested
        inner class InEnglish {
            @Test
            fun `contains the invitation url`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody)
                    .contains("https://haitaton.hel.fi/en/invitation?id=$invitationToken")
                assertThat(htmlBody)
                    .contains(
                        """<a href="https://haitaton.hel.fi/en/invitation?id=$invitationToken">"""
                    )
            }

            @Test
            fun `contains the signature`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).all {
                    containsLine(
                        "This email was generated automatically – please do not reply to this message."
                    )
                    containsLine("Kind regards,")
                    containsLine("City of Helsinki Urban Environment Division")
                    containsLine("Haitaton services")
                    containsLine("haitaton@hel.fi")
                }
                assertThat(htmlBody).all {
                    containsEscaped(
                        "This email was generated automatically – please do not reply to this message."
                    )
                    containsEscaped("Kind regards,")
                    containsEscaped("City of Helsinki Urban Environment Division")
                    containsEscaped("Haitaton services")
                    containsEscaped("haitaton@hel.fi")
                }
            }

            @Test
            fun `contains the inviter information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).all {
                    contains("$INVITER_NAME ($INVITER_EMAIL) has added you to the project")
                }
                assertThat(htmlBody).all {
                    containsEscaped("$INVITER_NAME ($INVITER_EMAIL) has added you to the project")
                }
            }

            @Test
            fun `contains the hanke information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).all {
                    contains("to the project ‘$HANKE_NIMI’ ($HANKE_TUNNUS).")
                }
                assertThat(htmlBody).all {
                    contains("to the project <b>&#x2018;$HANKE_NIMI&#x2019; ($HANKE_TUNNUS)</b>.")
                }
            }
        }
    }

    @Nested
    inner class ApplicationNotification {
        private val applicationNotification =
            ApplicationNotificationData(
                senderName = INVITER_NAME,
                senderEmail = "matti.meikalainen@test.fi",
                recipientEmail = TEST_EMAIL,
                applicationType = ApplicationType.CABLE_REPORT,
                applicationIdentifier = APPLICATION_IDENTIFIER,
                hankeTunnus = "HAI24-1",
            )

        private fun sendAndCapture(): MimeMessage {
            val email = slot<MimeMessage>()
            justRun { mailSender.send(capture(email)) }

            emailSenderService.sendApplicationNotificationEmail(applicationNotification)

            return email.captured
        }

        @Test
        fun `has the correct subject`() {
            val email = sendAndCapture()

            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Sinut on lisätty hakemukselle $APPLICATION_IDENTIFIER " +
                        "/ Du har lagts till i ansökan $APPLICATION_IDENTIFIER " +
                        "/ You have been added to application $APPLICATION_IDENTIFIER"
                )
        }

        @Test
        fun `has a header line in the body`() {
            val (textBody, htmlBody) = sendAndCapture().bodies()

            assertThat(textBody)
                .contains(
                    "Sinut on lisätty hakemukselle $APPLICATION_IDENTIFIER " +
                        "/ Du har lagts till i ansökan $APPLICATION_IDENTIFIER " +
                        "/ You have been added to application $APPLICATION_IDENTIFIER"
                )
            assertThat(htmlBody)
                .containsEscaped(
                    "Sinut on lisätty hakemukselle $APPLICATION_IDENTIFIER " +
                        "/ Du har lagts till i ansökan $APPLICATION_IDENTIFIER " +
                        "/ You have been added to application $APPLICATION_IDENTIFIER"
                )
        }

        @Nested
        inner class BodyInFinnish {
            @Test
            fun `contains sender information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains("$INVITER_NAME ($INVITER_EMAIL) on tehnyt ")
                assertThat(htmlBody).containsEscaped("$INVITER_NAME ($INVITER_EMAIL) on tehnyt ")
            }

            @Test
            fun `contains application information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody)
                    .contains(
                        "on tehnyt johtoselvityshakemuksen ($APPLICATION_IDENTIFIER) hankkeella"
                    )
                assertThat(htmlBody)
                    .containsEscaped(
                        "on tehnyt johtoselvityshakemuksen ($APPLICATION_IDENTIFIER) hankkeella"
                    )
            }

            @Test
            fun `contains hanke information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody)
                    .contains("hankkeella $HANKE_TUNNUS, ja lähettänyt sen käsittelyyn.")
                assertThat(htmlBody)
                    .containsEscaped("hankkeella $HANKE_TUNNUS, ja lähettänyt sen käsittelyyn.")
            }

            @Test
            fun `contains a link to haitaton`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody)
                    .contains("Tarkastele hakemusta Haitattomassa: https://haitaton.hel.fi")
                assertThat(htmlBody)
                    .contains(
                        """Tarkastele hakemusta Haitattomassa: <a href="https://haitaton.hel.fi">https://haitaton.hel.fi</a>"""
                    )
            }

            @Test
            fun `contains the signature`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).all {
                    containsLine("Tämä on automaattinen sähköposti – älä vastaa tähän viestiin.")
                    containsLine("Ystävällisin terveisin,")
                    containsLine("Helsingin kaupungin kaupunkiympäristön toimiala")
                    containsLine("Haitaton-asiointi")
                    containsLine("haitaton@hel.fi")
                }
                assertThat(htmlBody).all {
                    containsEscaped("Tämä on automaattinen sähköposti – älä vastaa tähän viestiin.")
                    containsEscaped("Ystävällisin terveisin,")
                    containsEscaped("Helsingin kaupungin kaupunkiympäristön toimiala")
                    containsEscaped("Haitaton-asiointi")
                    containsEscaped("haitaton@hel.fi")
                }
            }
        }

        @Nested
        inner class BodyInSwedish {
            @Test
            fun `contains sender information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains("$INVITER_NAME ($INVITER_EMAIL) har gjort en ansökan")
                assertThat(htmlBody)
                    .containsEscaped("$INVITER_NAME ($INVITER_EMAIL) har gjort en ansökan")
            }

            @Test
            fun `contains application information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody)
                    .contains(
                        "har gjort en ansökan om ledningsutredning ($APPLICATION_IDENTIFIER) i projektet"
                    )
                assertThat(htmlBody)
                    .containsEscaped(
                        "har gjort en ansökan om ledningsutredning ($APPLICATION_IDENTIFIER) i projektet"
                    )
            }

            @Test
            fun `contains hanke information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody)
                    .contains("i projektet $HANKE_TUNNUS och skickat in den för behandling.")
                assertThat(htmlBody)
                    .containsEscaped("i projektet $HANKE_TUNNUS och skickat in den för behandling.")
            }

            @Test
            fun `contains a link to haitaton`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody)
                    .contains("Kontrollera ansökan i Haitaton: https://haitaton.hel.fi")
                assertThat(htmlBody)
                    .contains(
                        """Kontrollera ans&ouml;kan i Haitaton: <a href="https://haitaton.hel.fi">https://haitaton.hel.fi</a>"""
                    )
            }

            @Test
            fun `contains the signature`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).all {
                    containsLine("Det här är ett automatiskt e-postmeddelande – svara inte på det.")
                    containsLine("Med vänlig hälsning,")
                    containsLine("Helsingfors stads stadsmiljösektor")
                    containsLine("Haitaton-ärenden")
                    containsLine("haitaton@hel.fi")
                }
                assertThat(htmlBody).all {
                    containsEscaped(
                        "Det här är ett automatiskt e-postmeddelande – svara inte på det."
                    )
                    containsEscaped("Med vänlig hälsning,")
                    containsEscaped("Helsingfors stads stadsmiljösektor")
                    containsEscaped("Haitaton-ärenden")
                    containsEscaped("haitaton@hel.fi")
                }
            }
        }

        @Nested
        inner class BodyInEnglish {
            @Test
            fun `contains sender information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains("$INVITER_NAME ($INVITER_EMAIL) has created")
                assertThat(htmlBody).containsEscaped("$INVITER_NAME ($INVITER_EMAIL) has created")
            }

            @Test
            fun `contains application information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody)
                    .contains(
                        "has created a cable report application ($APPLICATION_IDENTIFIER) for project"
                    )
                assertThat(htmlBody)
                    .containsEscaped(
                        "has created a cable report application ($APPLICATION_IDENTIFIER) for project"
                    )
            }

            @Test
            fun `contains hanke information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody)
                    .contains("for project $HANKE_TUNNUS and submitted it for processing.")
                assertThat(htmlBody)
                    .containsEscaped("for project $HANKE_TUNNUS and submitted it for processing.")
            }

            @Test
            fun `contains a link to haitaton`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody)
                    .contains(
                        "View the application in the Haitaton system: https://haitaton.hel.fi"
                    )
                assertThat(htmlBody)
                    .contains(
                        """View the application in the Haitaton system: <a href="https://haitaton.hel.fi">https://haitaton.hel.fi</a>"""
                    )
            }

            @Test
            fun `contains the signature`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).all {
                    containsLine(
                        "This email was generated automatically – please do not reply to this message."
                    )
                    containsLine("Kind regards,")
                    containsLine("City of Helsinki Urban Environment Division")
                    containsLine("Haitaton services")
                    containsLine("haitaton@hel.fi")
                }
                assertThat(htmlBody).all {
                    containsEscaped(
                        "This email was generated automatically – please do not reply to this message."
                    )
                    containsEscaped("Kind regards,")
                    containsEscaped("City of Helsinki Urban Environment Division")
                    containsEscaped("Haitaton services")
                    containsEscaped("haitaton@hel.fi")
                }
            }
        }
    }
}

/** Returns a (text body, HTML body) pair. */
fun MimeMessage.bodies(): Pair<String, String> {
    assertThat(content).isInstanceOf(MimeMultipart::class)
    val mp = content as MimeMultipart
    assertThat(mp.contentType).startsWith("multipart/mixed")
    assertThat(mp.count).isEqualTo(1)

    assertThat(mp.getBodyPart(0).content).isInstanceOf(MimeMultipart::class)
    val mp2 = mp.getBodyPart(0).content as MimeMultipart
    assertThat(mp2.contentType).startsWith("multipart/related")
    assertThat(mp2.count).isEqualTo(1)

    assertThat(mp2.getBodyPart(0).content).isInstanceOf(MimeMultipart::class)
    val mp3 = mp2.getBodyPart(0).content as MimeMultipart
    assertThat(mp3.contentType).startsWith("multipart/alternative")
    assertThat(mp3.count).isEqualTo(2)

    return listOf(mp3.getBodyPart(0), mp3.getBodyPart(1))
        .let { if (it[0].contentType.startsWith("text/plain")) it else it.reversed() }
        .map { it.content.toString() }
        .let { Pair(it[0], it[1]) }
}

fun Assert<String>.containsLine(expected: String) = contains("\n$expected\n")

fun Assert<String>.containsEscaped(expected: String) =
    contains(StringEscapeUtils.escapeHtml4(expected).replace("&ndash;", "&#x2013;"))

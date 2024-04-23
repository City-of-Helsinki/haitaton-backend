package fi.hel.haitaton.hanke.email

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsMatch
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.startsWith
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.mail.javamail.JavaMailSender

private const val INVITER_NAME = "Matti Meikäläinen"
private const val INVITER_EMAIL = "matti.meikalainen@test.fi"
private const val TEST_EMAIL = "test@test.test"
private const val HANKE_TUNNUS = "HAI24-1"
private const val HANKE_NIMI = "Mannerheimintien liikenneuudistus"

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

    private val encodedInviter = StringEscapeUtils.escapeHtml4(INVITER_NAME)

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

            val expectedHeader =
                "Sinut on lisätty hankkeelle HAI24-1 " +
                    "/ Du har lagts till i projektet HAI24-1 " +
                    "/ You have been added to project HAI24-1"
            assertThat(textBody).contains(expectedHeader)
            assertThat(htmlBody).contains(expectedHeader)
        }

        @Nested
        open inner class BodyInFinnish {
            open val invitationUrl = "https://haitaton.hel.fi/fi/kutsu?id=$invitationToken"

            open val signatureLines =
                listOf(
                    "Tämä on automaattinen sähköposti – älä vastaa tähän viestiin.",
                    "Ystävällisin terveisin,",
                    "Helsingin kaupungin kaupunkiympäristön toimiala",
                    "Haitaton-asiointi",
                    "haitaton@hel.fi",
                )

            open fun inviterInformation(name: String, email: String) =
                "$name ($email) lisäsi sinut hankkeelle"

            open val hankeInformation = "hankkeelle <b>$HANKE_NIMI ($HANKE_TUNNUS)</b>."

            @Test
            fun `contains the invitation url`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains(invitationUrl)
                assertThat(htmlBody)
                    .containsMatch(
                        """\Q<a href="$invitationUrl">\E\s*\Q$invitationUrl\E\s*</a>""".toRegex()
                    )
            }

            @Test
            fun `contains the signature`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains(signatureLines)
                assertThat(htmlBody).contains(signatureLines)
            }

            @Test
            open fun `contains the inviter information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains(inviterInformation(INVITER_NAME, INVITER_EMAIL))
                assertThat(htmlBody).contains(inviterInformation(encodedInviter, INVITER_EMAIL))
            }

            @Test
            open fun `contains the hanke information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody)
                    .contains(hankeInformation.replace("<b>", "").replace("</b>", ""))
                assertThat(htmlBody).contains(hankeInformation)
            }
        }

        @Nested
        inner class BodyInSwedish : BodyInFinnish() {
            override val invitationUrl = "https://haitaton.hel.fi/sv/inbjudan?id=$invitationToken"

            override val signatureLines =
                listOf(
                    "Det här är ett automatiskt e-postmeddelande – svara inte på det.",
                    "Med vänlig hälsning,",
                    "Helsingfors stads stadsmiljösektor",
                    "Haitaton-ärenden",
                    "haitaton@hel.fi",
                )

            override fun inviterInformation(name: String, email: String) =
                "$name ($email) lade till dig i projektet"

            override val hankeInformation = "i projektet <b>$HANKE_NIMI ($HANKE_TUNNUS)</b>."
        }

        @Nested
        inner class BodyInEnglish : BodyInFinnish() {
            override val invitationUrl = "https://haitaton.hel.fi/en/invitation?id=$invitationToken"

            override val signatureLines =
                listOf(
                    "This email was generated automatically – please do not reply to this message.",
                    "Kind regards,",
                    "Haitaton services",
                    "City of Helsinki Urban Environment Division",
                    "haitaton@hel.fi",
                )

            override fun inviterInformation(name: String, email: String) =
                "$name ($email) has added you to the project"

            override val hankeInformation = "to the project <b>‘$HANKE_NIMI’ ($HANKE_TUNNUS)</b>."
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
                hankeTunnus = HANKE_TUNNUS,
                hankeNimi = HANKE_NIMI,
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
                    "Haitaton: Sinut on lisätty hakemukselle " +
                        "/ Du har lagts till i en ansökan " +
                        "/ You have been added to an application"
                )
        }

        @Test
        fun `has a header line in the body`() {
            val (textBody, htmlBody) = sendAndCapture().bodies()

            val expectedBody =
                "Sinut on lisätty hakemukselle " +
                    "/ Du har lagts till i en ansökan " +
                    "/ You have been added to an application"
            assertThat(textBody).contains(expectedBody)
            assertThat(htmlBody).contains(expectedBody)
        }

        @Nested
        open inner class BodyInFinnish {
            open fun inviterInformation(name: String, email: String) =
                "$name ($email) on laatimassa "

            open val applicationInformation = "on laatimassa johtoselvityshakemusta hankkeelle"
            open val hankeInformationText =
                "hankkeelle \"$HANKE_NIMI\" ($HANKE_TUNNUS). Sinut on lisätty hakemukselle."
            open val hankeInformationHtml =
                "hankkeelle <b>$HANKE_NIMI ($HANKE_TUNNUS)</b>. Sinut on lisätty hakemukselle."
            open val linkPrefix = "Tarkastele hakemusta Haitattomassa:"
            open val signatureLines =
                listOf(
                    "Tämä on automaattinen sähköposti – älä vastaa tähän viestiin.",
                    "Ystävällisin terveisin,",
                    "Helsingin kaupungin kaupunkiympäristön toimiala",
                    "Haitaton-asiointi",
                    "haitaton@hel.fi",
                )

            @Test
            fun `contains sender information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains(inviterInformation(INVITER_NAME, INVITER_EMAIL))
                assertThat(htmlBody).contains(inviterInformation(encodedInviter, INVITER_EMAIL))
            }

            @Test
            open fun `contains application information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains(applicationInformation)
                assertThat(htmlBody).contains(applicationInformation)
            }

            @Test
            open fun `contains hanke information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains(hankeInformationText)
                assertThat(htmlBody).contains(hankeInformationHtml)
            }

            @Test
            open fun `contains a link to haitaton`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains("$linkPrefix https://haitaton.hel.fi")
                assertThat(htmlBody)
                    .contains(
                        """$linkPrefix <a href="https://haitaton.hel.fi">https://haitaton.hel.fi</a>"""
                    )
            }

            @Test
            open fun `contains the signature`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains(signatureLines)
                assertThat(htmlBody).contains(signatureLines)
            }
        }

        @Nested
        inner class BodyInSwedish : BodyInFinnish() {
            override fun inviterInformation(name: String, email: String) = "$name ($email) gör"

            override val applicationInformation = "gör ledningsutredning till projektet"
            override val hankeInformationText =
                "till projektet \"$HANKE_NIMI\" ($HANKE_TUNNUS). Du har lagts till i ansökan."
            override val hankeInformationHtml =
                "till projektet <b>$HANKE_NIMI ($HANKE_TUNNUS)</b>. Du har lagts till i ansökan."
            override val linkPrefix = "Kontrollera ansökan i Haitaton:"
            override val signatureLines =
                listOf(
                    "Det här är ett automatiskt e-postmeddelande – svara inte på det.",
                    "Med vänlig hälsning,",
                    "Helsingfors stads stadsmiljösektor",
                    "Haitaton-ärenden",
                    "haitaton@hel.fi",
                )
        }

        @Nested
        inner class BodyInEnglish : BodyInFinnish() {
            override fun inviterInformation(name: String, email: String) =
                "$name ($email) is preparing"

            override val applicationInformation =
                "is preparing a cable report application for project"
            override val hankeInformationText =
                "for project \"$HANKE_NIMI\" ($HANKE_TUNNUS). You have been added to the application."
            override val hankeInformationHtml =
                "for project <b>$HANKE_NIMI ($HANKE_TUNNUS)</b>. You have been added to the application."
            override val linkPrefix = "View the application in the Haitaton system:"
            override val signatureLines =
                listOf(
                    "This email was generated automatically – please do not reply to this message.",
                    "Kind regards,",
                    "Haitaton services",
                    "City of Helsinki Urban Environment Division",
                    "haitaton@hel.fi",
                )
        }
    }

    @Nested
    inner class AccessRightsUpdateNotification {
        private val notification =
            AccessRightsUpdateNotificationData(
                recipientEmail = TEST_EMAIL,
                hankeTunnus = HANKE_TUNNUS,
                hankeNimi = HANKE_NIMI,
                updatedByName = INVITER_NAME,
                updatedByEmail = INVITER_EMAIL,
                newAccessRights = Kayttooikeustaso.HANKEMUOKKAUS,
            )

        private fun sendAndCapture(): MimeMessage {
            val email = slot<MimeMessage>()
            justRun { mailSender.send(capture(email)) }

            emailSenderService.sendAccessRightsUpdateNotificationEmail(notification)

            return email.captured
        }

        @Test
        fun `has the correct subject`() {
            val email = sendAndCapture()

            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Käyttöoikeustasoasi on muutettu ($HANKE_TUNNUS) / Dina användarrättigheter har förändrats ($HANKE_TUNNUS) / Your access right level has been changed ($HANKE_TUNNUS)"
                )
        }

        @Test
        fun `has a header line in the body`() {
            val (textBody, htmlBody) = sendAndCapture().bodies()

            val expectedBody =
                "Käyttöoikeustasoasi on muutettu ($HANKE_TUNNUS) / Dina användarrättigheter har förändrats ($HANKE_TUNNUS) / Your access right level has been changed ($HANKE_TUNNUS)"
            assertThat(textBody).contains(expectedBody)
            assertThat(htmlBody).contains(expectedBody)
        }

        @Nested
        open inner class BodyInFinnish {
            open fun updatedByInformation(name: String, email: String) =
                "$name ($email) on muuttanut"

            open val updateInformationText =
                "käyttöoikeustasoasi hankkeella \"$HANKE_NIMI\" ($HANKE_TUNNUS). Uusi käyttöoikeutesi on \"Hankemuokkaus\"."

            open val updateInformationHtml =
                "käyttöoikeustasoasi hankkeella <b>$HANKE_NIMI ($HANKE_TUNNUS)</b>. Uusi käyttöoikeutesi on <b>Hankemuokkaus</b>."

            open val linkPrefix = "Tarkastele hanketta täällä:"

            open val hankeLink = "https://haitaton.hel.fi/fi/hankesalkku/$HANKE_TUNNUS"

            open val signatureLines =
                listOf(
                    "Tämä on automaattinen sähköposti – älä vastaa tähän viestiin.",
                    "Ystävällisin terveisin,",
                    "Helsingin kaupungin kaupunkiympäristön toimiala",
                    "Haitaton-asiointi",
                    "haitaton@hel.fi",
                )

            @Test
            fun `contains updated-by information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains(updatedByInformation(INVITER_NAME, INVITER_EMAIL))
                assertThat(htmlBody).contains(updatedByInformation(encodedInviter, INVITER_EMAIL))
            }

            @Test
            open fun `contains update information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains(updateInformationText)
                assertThat(htmlBody).contains(updateInformationHtml)
            }

            @Test
            open fun `contains a link to hanke`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains("$linkPrefix $hankeLink")
                assertThat(htmlBody).contains("""$linkPrefix <a href="$hankeLink">$hankeLink</a>""")
            }

            @Test
            open fun `contains the signature`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains(signatureLines)
                assertThat(htmlBody).contains(signatureLines)
            }
        }

        @Nested
        inner class BodyInSwedish : BodyInFinnish() {
            override fun updatedByInformation(name: String, email: String) =
                "$name ($email) har ändrat"

            override val updateInformationText =
                "dina användarrättigheter på projektet \"$HANKE_NIMI\" ($HANKE_TUNNUS). Din nya användarrättighet är \"Ändring i projekt\"."

            override val updateInformationHtml =
                "dina användarrättigheter på projektet <b>$HANKE_NIMI ($HANKE_TUNNUS)</b>. Din nya användarrättighet är <b>${StringEscapeUtils.escapeHtml4("Ändring i projekt")}</b>."

            override val linkPrefix = "Granska projektet här:"

            override val hankeLink = "https://haitaton.hel.fi/sv/projektportfolj/$HANKE_TUNNUS"

            override val signatureLines =
                listOf(
                    "Det här är ett automatiskt e-postmeddelande – svara inte på det.",
                    "Med vänlig hälsning,",
                    "Helsingfors stads stadsmiljösektor",
                    "Haitaton-ärenden",
                    "haitaton@hel.fi",
                )
        }

        @Nested
        inner class BodyInEnglish : BodyInFinnish() {
            override fun updatedByInformation(name: String, email: String) =
                "$name ($email) has changed"

            override val updateInformationText =
                "your access right level for the project \"$HANKE_NIMI\" ($HANKE_TUNNUS). Your new access right level is \"Project editing\"."

            override val updateInformationHtml =
                "your access right level for the project <b>$HANKE_NIMI ($HANKE_TUNNUS)</b>. Your new access right level is <b>Project editing</b>."

            override val linkPrefix = "View the project here:"

            override val hankeLink = "https://haitaton.hel.fi/en/projectportfolio/$HANKE_TUNNUS"

            override val signatureLines =
                listOf(
                    "This email was generated automatically – please do not reply to this message.",
                    "Kind regards,",
                    "Haitaton services",
                    "City of Helsinki Urban Environment Division",
                    "haitaton@hel.fi",
                )
        }
    }

    @Nested
    inner class RemovalFromHanke {
        private val notification =
            RemovalFromHankeNotificationData(
                recipientEmail = TEST_EMAIL,
                hankeTunnus = HANKE_TUNNUS,
                hankeNimi = HANKE_NIMI,
                deletedByName = INVITER_NAME,
                deletedByEmail = INVITER_EMAIL,
            )

        private fun sendAndCapture(): MimeMessage {
            val email = slot<MimeMessage>()
            justRun { mailSender.send(capture(email)) }

            emailSenderService.sendRemovalFromHankeNotificationEmail(notification)

            return email.captured
        }

        @Test
        fun `has the correct subject`() {
            val email = sendAndCapture()

            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Sinut on poistettu hankkeelta ($HANKE_TUNNUS) / Du har tagits bort från projektet ($HANKE_TUNNUS) / You have been removed from the project ($HANKE_TUNNUS)"
                )
        }

        @Test
        fun `has a header line in the body`() {
            val (textBody, htmlBody) = sendAndCapture().bodies()

            val expectedBody =
                "Sinut on poistettu hankkeelta ($HANKE_TUNNUS) / Du har tagits bort från projektet ($HANKE_TUNNUS) / You have been removed from the project ($HANKE_TUNNUS)"
            assertThat(textBody).contains(expectedBody)
            assertThat(htmlBody).contains(expectedBody)
        }

        @Nested
        open inner class BodyInFinnish {
            open fun deletedByInformation(name: String, email: String) =
                "$name ($email) on poistanut sinut"

            open val deleteInformationText =
                "hankkeelta \"$HANKE_NIMI\" ($HANKE_TUNNUS), eikä sinulla ole enää pääsyä hankkeelle."

            open val deleteInformationHtml =
                "hankkeelta <b>$HANKE_NIMI ($HANKE_TUNNUS)</b>, eikä sinulla ole enää pääsyä hankkeelle."

            open val signatureLines =
                listOf(
                    "Tämä on automaattinen sähköposti – älä vastaa tähän viestiin.",
                    "Ystävällisin terveisin,",
                    "Helsingin kaupungin kaupunkiympäristön toimiala",
                    "Haitaton-asiointi",
                    "haitaton@hel.fi",
                )

            @Test
            fun `contains deleted-by information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains(deletedByInformation(INVITER_NAME, INVITER_EMAIL))
                assertThat(htmlBody).contains(deletedByInformation(encodedInviter, INVITER_EMAIL))
            }

            @Test
            open fun `contains delete information`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains(deleteInformationText)
                assertThat(htmlBody).contains(deleteInformationHtml)
            }

            @Test
            open fun `contains the signature`() {
                val (textBody, htmlBody) = sendAndCapture().bodies()

                assertThat(textBody).contains(signatureLines)
                assertThat(htmlBody).contains(signatureLines)
            }
        }

        @Nested
        inner class BodyInSwedish : BodyInFinnish() {
            override fun deletedByInformation(name: String, email: String) =
                "$name ($email) har tagit bort dig"

            override val deleteInformationText =
                "från projektet \"$HANKE_NIMI\" ($HANKE_TUNNUS) och du har tagit bort dig från projektet."

            override val deleteInformationHtml =
                "från projektet <b>$HANKE_NIMI ($HANKE_TUNNUS)</b> och du har tagit bort dig från projektet."

            override val signatureLines =
                listOf(
                    "Det här är ett automatiskt e-postmeddelande – svara inte på det.",
                    "Med vänlig hälsning,",
                    "Helsingfors stads stadsmiljösektor",
                    "Haitaton-ärenden",
                    "haitaton@hel.fi",
                )
        }

        @Nested
        inner class BodyInEnglish : BodyInFinnish() {
            override fun deletedByInformation(name: String, email: String) =
                "$name ($email) has removed you"

            override val deleteInformationText =
                "from the project \"$HANKE_NIMI\" ($HANKE_TUNNUS), and you no longer have access to the project."

            override val deleteInformationHtml =
                "from the project <b>$HANKE_NIMI ($HANKE_TUNNUS)</b>, and you no longer have access to the project."

            override val signatureLines =
                listOf(
                    "This email was generated automatically – please do not reply to this message.",
                    "Kind regards,",
                    "Haitaton services",
                    "City of Helsinki Urban Environment Division",
                    "haitaton@hel.fi",
                )
        }
    }

    @Nested
    inner class ExpandAsterisks {
        @ParameterizedTest
        @CsvSource(
            "kake@katselu.test, \\Qkake@katselu.test\\E",
            "*@katselu.test,    .*\\Q@katselu.test\\E",
            "*@*.test,          .*\\Q@\\E.*\\Q.test\\E",
            "kake@katselu.*,    \\Qkake@katselu.\\E.*",
            "**@katselu.test,   .*.*\\Q@katselu.test\\E",
            "*@k*t*s*l*.test,   .*\\Q@k\\E.*\\Qt\\E.*\\Qs\\E.*\\Ql\\E.*\\Q.test\\E",
        )
        fun `returns correct output`(input: String, expected: String) {
            val result = EmailSenderService.expandAsterisks(input)

            assertThat(result).isEqualTo(expected)
        }
    }
}

fun MimeMessage.textBody(): String = bodies().first

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

package fi.hel.haitaton.hanke.email

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.email.EmailSenderService.Companion.translations
import fi.hel.haitaton.hanke.firstReceivedMessage
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import org.apache.commons.text.StringEscapeUtils
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired

private const val TEST_EMAIL = "test@test.test"
private const val HAITATON_NO_REPLY = "no-reply@hel.fi"
private const val APPLICATION_IDENTIFIER = "JS2300001"
private const val KAIVUILMOITUS_TUNNUS = "KP2300001"
private const val INVITER_NAME = "Matti Meikäläinen"
private const val INVITER_EMAIL = "matti.meikalainen@test.fi"
private const val HANKE_TUNNUS = "HAI24-1"
private const val HANKE_NIMI = "Mannerheimintien liikenneuudistus"

class EmailSenderServiceITest : IntegrationTest() {

    companion object {
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
    }

    @Autowired lateinit var emailSenderService: EmailSenderService

    @Nested
    inner class JohtoSelvitysComplete {
        @Test
        fun `sendJohtoselvitysCompleteEmail sends email with correct recipient`() {
            emailSenderService.sendJohtoselvitysCompleteEmail(
                JohtoselvitysCompleteEmail(TEST_EMAIL, 13L, APPLICATION_IDENTIFIER)
            )

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(TEST_EMAIL)
        }

        @Test
        fun `sendJohtoselvitysCompleteEmail sends email with sender from properties`() {
            emailSenderService.sendJohtoselvitysCompleteEmail(
                JohtoselvitysCompleteEmail(TEST_EMAIL, 13L, APPLICATION_IDENTIFIER)
            )

            val email = greenMail.firstReceivedMessage()
            assertThat(email.from).hasSize(1)
            assertThat(email.from[0].toString()).isEqualTo(HAITATON_NO_REPLY)
        }

        @Test
        fun `sendJohtoselvitysCompleteEmail sends email with correct subject`() {
            emailSenderService.sendJohtoselvitysCompleteEmail(
                JohtoselvitysCompleteEmail(TEST_EMAIL, 13L, APPLICATION_IDENTIFIER)
            )

            val email = greenMail.firstReceivedMessage()
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Johtoselvitys JS2300001 / Ledningsutredning JS2300001 / Cable report JS2300001"
                )
        }

        @Test
        fun `sendJohtoselvitysCompleteEmail sends email with parametrized hybrid body`() {
            emailSenderService.sendJohtoselvitysCompleteEmail(
                JohtoselvitysCompleteEmail(TEST_EMAIL, 13L, APPLICATION_IDENTIFIER)
            )

            val email = greenMail.firstReceivedMessage()
            val (textBody, htmlBody) = email.bodies()
            assertThat(textBody).all {
                contains(APPLICATION_IDENTIFIER)
                contains("http://localhost:3001/fi/hakemus/13")
                contains("http://localhost:3001/sv/ansokan/13")
                contains("http://localhost:3001/en/application/13")
            }
            // Compress all whitespace into single spaces so that they don't interfere with
            // matching.
            val squashedHtmlBody = htmlBody.replace("\\s+".toRegex(), " ")
            assertThat(squashedHtmlBody).all {
                contains(APPLICATION_IDENTIFIER)
                contains("""<a href="http://localhost:3001/fi/hakemus/13">""")
                contains("""<a href="http://localhost:3001/sv/ansokan/13">""")
                contains("""<a href="http://localhost:3001/en/application/13">""")
            }
        }
    }

    @Nested
    inner class SendKaivuilmoitusDecisionEmail {
        @Test
        fun `sends email with correct recipient`() {
            emailSenderService.sendKaivuilmoitusDecisionEmail(
                KaivuilmoitusDecisionEmail(TEST_EMAIL, 13L, KAIVUILMOITUS_TUNNUS)
            )

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(TEST_EMAIL)
        }

        @Test
        fun `sends email with sender from properties`() {
            emailSenderService.sendKaivuilmoitusDecisionEmail(
                KaivuilmoitusDecisionEmail(TEST_EMAIL, 13L, KAIVUILMOITUS_TUNNUS)
            )

            val email = greenMail.firstReceivedMessage()
            assertThat(email.from).hasSize(1)
            assertThat(email.from[0].toString()).isEqualTo(HAITATON_NO_REPLY)
        }

        @Test
        fun `sends email with correct subject`() {
            emailSenderService.sendKaivuilmoitusDecisionEmail(
                KaivuilmoitusDecisionEmail(TEST_EMAIL, 13L, KAIVUILMOITUS_TUNNUS)
            )

            val email = greenMail.firstReceivedMessage()
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Kaivuilmoitukseen KP2300001 liittyvä päätös on ladattavissa / Beslut om grävningsanmälan KP2300001 kan laddas ner / The decision concerning an excavation notification KP2300001 can be downloaded"
                )
        }

        @Test
        fun `sends email with parametrized hybrid body`() {
            emailSenderService.sendKaivuilmoitusDecisionEmail(
                KaivuilmoitusDecisionEmail(TEST_EMAIL, 13L, KAIVUILMOITUS_TUNNUS)
            )

            val email = greenMail.firstReceivedMessage()
            val (textBody, htmlBody) = email.bodies()
            assertThat(textBody).all {
                contains(KAIVUILMOITUS_TUNNUS)
                contains("http://localhost:3001/fi/hakemus/13")
                contains("http://localhost:3001/sv/ansokan/13")
                contains("http://localhost:3001/en/application/13")
            }
            // Compress all whitespace into single spaces so that they don't interfere with
            // matching.
            val squashedHtmlBody = htmlBody.replace("\\s+".toRegex(), " ")
            assertThat(squashedHtmlBody).all {
                contains(KAIVUILMOITUS_TUNNUS)
                contains("""<a href="http://localhost:3001/fi/hakemus/13">""")
                contains("""<a href="http://localhost:3001/sv/ansokan/13">""")
                contains("""<a href="http://localhost:3001/en/application/13">""")
            }
        }
    }

    @Nested
    inner class HankeInvitation {
        private val notification =
            HankeInvitationEmail(
                inviterName = INVITER_NAME,
                inviterEmail = INVITER_EMAIL,
                to = TEST_EMAIL,
                hankeTunnus = HANKE_TUNNUS,
                hankeNimi = HANKE_NIMI,
                invitationToken = "MgtzRbcPsvoKQamnaSxCnmW7",
            )

        @Test
        fun `sendHankeInvitationEmail sends email with correct recipient`() {
            emailSenderService.sendHankeInvitationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(TEST_EMAIL)
        }

        @Test
        fun `sendHankeInvitationEmail sends email with sender from properties`() {
            emailSenderService.sendHankeInvitationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.from).hasSize(1)
            assertThat(email.from[0].toString()).isEqualTo(HAITATON_NO_REPLY)
        }

        @Test
        fun `sendHankeInvitationEmail sends email with correct subject`() {
            emailSenderService.sendHankeInvitationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Sinut on kutsuttu hankkeelle HAI24-1 " +
                        "/ Du har blivit inbjuden till projektet HAI24-1 " +
                        "/ You have been invited to project HAI24-1"
                )
        }

        @Test
        fun `sendHankeInvitationEmail sends email with parametrized hybrid body`() {
            emailSenderService.sendHankeInvitationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            val (textBody, htmlBody) = email.bodies()
            assertThat(textBody).all {
                contains(
                    "${notification.inviterName} (${notification.inviterEmail}) on kutsunut sinut"
                )
                contains("hankkeelle ${notification.hankeNimi} (${notification.hankeTunnus}).")
                contains("http://localhost:3001/fi/kutsu?id=${notification.invitationToken}")
            }
            assertThat(htmlBody).all {
                val htmlEscapedName = "Matti Meik&auml;l&auml;inen"
                contains("$htmlEscapedName (${notification.inviterEmail})")
                contains(
                    "hankkeelle <b>${notification.hankeNimi} (${notification.hankeTunnus})</b>."
                )
                contains(
                    """<a href="http://localhost:3001/fi/kutsu?id=${notification.invitationToken}">"""
                )
            }
        }
    }

    @Nested
    inner class ApplicationNotification {
        private val notification =
            ApplicationNotificationEmail(
                senderName = INVITER_NAME,
                senderEmail = INVITER_EMAIL,
                to = TEST_EMAIL,
                applicationType = ApplicationType.CABLE_REPORT,
                hankeTunnus = HANKE_TUNNUS,
                hankeNimi = HANKE_NIMI,
            )

        @Test
        fun `sendApplicationNotificationEmail sends email with correct recipient`() {
            emailSenderService.sendApplicationNotificationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(TEST_EMAIL)
        }

        @Test
        fun `sendApplicationNotificationEmail sends email with sender from properties`() {
            emailSenderService.sendApplicationNotificationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.from).hasSize(1)
            assertThat(email.from[0].toString()).isEqualTo(HAITATON_NO_REPLY)
        }

        @Test
        fun `sendApplicationNotificationEmail sends email with correct subject`() {
            emailSenderService.sendApplicationNotificationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Sinut on lisätty hakemukselle " +
                        "/ Du har lagts till i en ansökan " +
                        "/ You have been added to an application"
                )
        }

        @Test
        fun `sendApplicationNotificationEmail sends email with parametrized hybrid body`() {
            emailSenderService.sendApplicationNotificationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            val (textBody, htmlBody) = email.bodies()
            assertThat(textBody).all {
                contains("${notification.senderName} (${notification.senderEmail}) on")
                contains(
                    "laatimassa johtoselvityshakemusta hankkeelle \"${notification.hankeNimi}\" (${notification.hankeTunnus})"
                )
                contains("Tarkastele hakemusta Haitattomassa: http://localhost:3001")
            }
            assertThat(htmlBody).all {
                val htmlEscapedName = "Matti Meik&auml;l&auml;inen"
                contains("$htmlEscapedName (${notification.senderEmail})")
                contains(
                    "laatimassa johtoselvityshakemusta hankkeelle <b>${notification.hankeNimi} (${notification.hankeTunnus})</b>"
                )
                contains("""<a href="http://localhost:3001">""")
            }
        }
    }

    @Nested
    inner class AccessRightsUpdateNotification {
        private val notification =
            AccessRightsUpdateNotificationEmail(
                to = TEST_EMAIL,
                hankeTunnus = HANKE_TUNNUS,
                hankeNimi = HANKE_NIMI,
                updatedByName = INVITER_NAME,
                updatedByEmail = INVITER_EMAIL,
                newAccessRights = Kayttooikeustaso.HANKEMUOKKAUS,
            )

        @Test
        fun `Send email with correct recipient`() {
            emailSenderService.sendAccessRightsUpdateNotificationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(TEST_EMAIL)
        }

        @Test
        fun `Send email with sender from properties`() {
            emailSenderService.sendAccessRightsUpdateNotificationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.from).hasSize(1)
            assertThat(email.from[0].toString()).isEqualTo(HAITATON_NO_REPLY)
        }

        @Test
        fun `Send email with correct subject`() {
            emailSenderService.sendAccessRightsUpdateNotificationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Käyttöoikeustasoasi on muutettu (HAI24-1) / Dina användarrättigheter har förändrats (HAI24-1) / Your access right level has been changed (HAI24-1)"
                )
        }

        @Test
        fun `Send email with parametrized hybrid body`() {
            emailSenderService.sendAccessRightsUpdateNotificationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            val (textBody, htmlBody) = email.bodies()
            assertThat(textBody).all {
                contains("${notification.updatedByName} (${notification.updatedByEmail}) on")
                contains(
                    "muuttanut käyttöoikeustasoasi hankkeella \"${notification.hankeNimi}\" (${notification.hankeTunnus})"
                )
                contains(
                    "Uusi käyttöoikeutesi on \"${notification.newAccessRights.translations().fi}\""
                )
                contains(
                    "Tarkastele hanketta täällä: http://localhost:3001/fi/hankesalkku/${notification.hankeTunnus}"
                )
            }
            assertThat(htmlBody).all {
                contains(
                    "${StringEscapeUtils.escapeHtml4(notification.updatedByName)} (${notification.updatedByEmail}) on"
                )
                contains(
                    "muuttanut käyttöoikeustasoasi hankkeella <b>${notification.hankeNimi} (${notification.hankeTunnus})</b>"
                )
                contains(
                    "Uusi käyttöoikeutesi on <b>${notification.newAccessRights.translations().fi}</b>"
                )
                contains(
                    "Tarkastele hanketta täällä: <a href=\"http://localhost:3001/fi/hankesalkku/${notification.hankeTunnus}\">http://localhost:3001/fi/hankesalkku/${notification.hankeTunnus}</a>"
                )
            }
        }
    }

    @Nested
    inner class RemovalFromHankeNotification {
        private val notification =
            RemovalFromHankeNotificationEmail(
                to = TEST_EMAIL,
                hankeTunnus = HANKE_TUNNUS,
                hankeNimi = HANKE_NIMI,
                deletedByName = INVITER_NAME,
                deletedByEmail = INVITER_EMAIL,
            )

        @Test
        fun `Send email with correct recipient`() {
            emailSenderService.sendRemovalFromHankeNotificationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(TEST_EMAIL)
        }

        @Test
        fun `Send email with sender from properties`() {
            emailSenderService.sendRemovalFromHankeNotificationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.from).hasSize(1)
            assertThat(email.from[0].toString()).isEqualTo(HAITATON_NO_REPLY)
        }

        @Test
        fun `Send email with correct subject`() {
            emailSenderService.sendRemovalFromHankeNotificationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Sinut on poistettu hankkeelta (HAI24-1) / Du har tagits bort från projektet (HAI24-1) / You have been removed from the project (HAI24-1)"
                )
        }

        @Test
        fun `Send email with parametrized hybrid body`() {
            emailSenderService.sendRemovalFromHankeNotificationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            val (textBody, htmlBody) = email.bodies()
            assertThat(textBody).all {
                contains("${notification.deletedByName} (${notification.deletedByEmail}) on")
                contains(
                    "poistanut sinut hankkeelta \"${notification.hankeNimi}\" (${notification.hankeTunnus})"
                )
            }
            assertThat(htmlBody).all {
                contains(
                    "${StringEscapeUtils.escapeHtml4(notification.deletedByName)} (${notification.deletedByEmail}) on"
                )
                contains(
                    "poistanut sinut hankkeelta <b>${notification.hankeNimi} (${notification.hankeTunnus})</b>"
                )
            }
        }
    }

    @Nested
    inner class InformationRequest {
        private val notification =
            InformationRequestEmail(
                to = TEST_EMAIL,
                hakemusNimi = HANKE_NIMI,
                hakemusTunnus = APPLICATION_IDENTIFIER,
                hakemusId = 13L,
            )

        @Test
        fun `Send email with correct recipient`() {
            emailSenderService.sendInformationRequestEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(TEST_EMAIL)
        }

        @Test
        fun `Send email with sender from properties`() {
            emailSenderService.sendInformationRequestEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.from).hasSize(1)
            assertThat(email.from[0].toString()).isEqualTo(HAITATON_NO_REPLY)
        }

        @Test
        fun `Send email with correct subject`() {
            emailSenderService.sendInformationRequestEmail(notification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Hakemuksellesi on tullut täydennyspyyntö / Din ansökan har fått en begäran om komplettering / There is a request for supplementary information for your application"
                )
        }

        @Test
        fun `Send email with parametrized hybrid body`() {
            emailSenderService.sendInformationRequestEmail(notification)

            val email = greenMail.firstReceivedMessage()
            val (textBody, htmlBody) = email.bodies()
            assertThat(textBody).all {
                contains(
                    "Hakemuksellesi $HANKE_NIMI ($APPLICATION_IDENTIFIER) on tullut täydennyspyyntö"
                )
                contains(
                    "Käy vastaamassa siihen Haitattomassa: http://localhost:3001/fi/hakemus/13"
                )
                contains("Svara på den i Haitaton: http://localhost:3001/sv/ansokan/13")
                contains("Please reply to it in Haitaton: http://localhost:3001/en/application/13")
            }
            assertThat(htmlBody).all {
                contains(
                    "Hakemuksellesi $HANKE_NIMI ($APPLICATION_IDENTIFIER) on tullut täydennyspyyntö"
                )
                contains(
                    """Käy vastaamassa siihen Haitattomassa: <a href="http://localhost:3001/fi/hakemus/13">http://localhost:3001/fi/hakemus/13</a>"""
                )
                contains(
                    """Svara på den i Haitaton: <a href="http://localhost:3001/sv/ansokan/13">http://localhost:3001/sv/ansokan/13</a>"""
                )
                contains(
                    """Please reply to it in Haitaton: <a href="http://localhost:3001/en/application/13">http://localhost:3001/en/application/13</a>"""
                )
            }
        }
    }
}

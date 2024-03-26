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
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.email.EmailSenderService.Companion.translations
import fi.hel.haitaton.hanke.firstReceivedMessage
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import org.apache.commons.text.StringEscapeUtils
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired

private const val TEST_EMAIL = "test@test.test"
private const val HAITATON_NO_REPLY = "no-reply@hel.fi"
private const val APPLICATION_IDENTIFIER = "JS2300001"
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
                TEST_EMAIL,
                13L,
                APPLICATION_IDENTIFIER
            )

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(TEST_EMAIL)
        }

        @Test
        fun `sendJohtoselvitysCompleteEmail sends email with sender from properties`() {
            emailSenderService.sendJohtoselvitysCompleteEmail(
                TEST_EMAIL,
                13L,
                APPLICATION_IDENTIFIER
            )

            val email = greenMail.firstReceivedMessage()
            assertThat(email.from).hasSize(1)
            assertThat(email.from[0].toString()).isEqualTo(HAITATON_NO_REPLY)
        }

        @Test
        fun `sendJohtoselvitysCompleteEmail sends email with correct subject`() {
            emailSenderService.sendJohtoselvitysCompleteEmail(
                TEST_EMAIL,
                13L,
                APPLICATION_IDENTIFIER
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
                TEST_EMAIL,
                13L,
                APPLICATION_IDENTIFIER
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
    inner class HankeInvitation {
        private val notification =
            HankeInvitationData(
                inviterName = INVITER_NAME,
                inviterEmail = INVITER_EMAIL,
                recipientEmail = TEST_EMAIL,
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
                    "Haitaton: Sinut on lisätty hankkeelle HAI24-1 " +
                        "/ Du har lagts till i projektet HAI24-1 " +
                        "/ You have been added to project HAI24-1"
                )
        }

        @Test
        fun `sendHankeInvitationEmail sends email with parametrized hybrid body`() {
            emailSenderService.sendHankeInvitationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            val (textBody, htmlBody) = email.bodies()
            assertThat(textBody).all {
                contains("${notification.inviterName} (${notification.inviterEmail}) lisäsi sinut")
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
            ApplicationNotificationData(
                senderName = INVITER_NAME,
                senderEmail = INVITER_EMAIL,
                recipientEmail = TEST_EMAIL,
                applicationType = ApplicationType.CABLE_REPORT,
                applicationIdentifier = APPLICATION_IDENTIFIER,
                hankeTunnus = HANKE_TUNNUS,
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
                    "Haitaton: Sinut on lisätty hakemukselle JS2300001 " +
                        "/ Du har lagts till i ansökan JS2300001 " +
                        "/ You have been added to application JS2300001"
                )
        }

        @Test
        fun `sendApplicationNotificationEmail sends email with parametrized hybrid body`() {
            emailSenderService.sendApplicationNotificationEmail(notification)

            val email = greenMail.firstReceivedMessage()
            val (textBody, htmlBody) = email.bodies()
            assertThat(textBody).all {
                contains("${notification.senderName} (${notification.senderEmail}) on")
                contains("tehnyt johtoselvityshakemuksen (${notification.applicationIdentifier})")
                contains("hankkeella ${notification.hankeTunnus}")
                contains("Tarkastele hakemusta Haitattomassa: http://localhost:3001")
            }
            assertThat(htmlBody).all {
                val htmlEscapedName = "Matti Meik&auml;l&auml;inen"
                contains("$htmlEscapedName (${notification.senderEmail})")
                contains("johtoselvityshakemuksen (${notification.applicationIdentifier})")
                contains("""<a href="http://localhost:3001">""")
            }
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
            // TODO needs translations
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Käyttöoikeustasoasi on muutettu (HAI24-1) / Käyttöoikeustasoasi on muutettu (HAI24-1) / Käyttöoikeustasoasi on muutettu (HAI24-1)"
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
            RemovalFromHankeNotificationData(
                recipientEmail = TEST_EMAIL,
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
            // TODO needs translations
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Sinut on poistettu hankkeelta (HAI24-1) / Sinut on poistettu hankkeelta (HAI24-1) / Sinut on poistettu hankkeelta (HAI24-1)"
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
}

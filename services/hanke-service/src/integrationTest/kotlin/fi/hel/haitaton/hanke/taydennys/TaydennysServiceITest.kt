package fi.hel.haitaton.hanke.taydennys

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.InformationRequestFieldKey
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import io.mockk.every
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TaydennysServiceITest(
    @Autowired private val taydennysService: TaydennysService,
    @Autowired private val taydennyspyyntoRepository: TaydennyspyyntoRepository,
    @Autowired private val alluClient: AlluClient,
    @Autowired private val hakemusFactory: HakemusFactory,
) : IntegrationTest() {
    private val alluId = 3464

    @Nested
    inner class SaveTaydennyspyyntoFromAllu {

        @Test
        fun `saves taydennyspyynto when Allu has one for the application`() {
            val hakemus = hakemusFactory.builder().withStatus(alluId = alluId).save()
            val kentat =
                listOf(
                    AlluFactory.createInformationRequestField(
                        InformationRequestFieldKey.CUSTOMER, "Customer is missing"),
                    AlluFactory.createInformationRequestField(
                        InformationRequestFieldKey.ATTACHMENT, "Needs a letter of attorney"),
                )
            every { alluClient.getInformationRequest(hakemus.alluid!!) } returns
                AlluFactory.createInformationRequest(applicationAlluId = alluId, fields = kentat)

            taydennysService.saveTaydennyspyyntoFromAllu(hakemus)

            assertThat(taydennyspyyntoRepository.findAll()).single().all {
                prop(TaydennyspyyntoEntity::alluId)
                    .isEqualTo(AlluFactory.DEFAULT_INFORMATION_REQUEST_ID)
                prop(TaydennyspyyntoEntity::applicationId).isEqualTo(hakemus.id)
                prop(TaydennyspyyntoEntity::kentat)
                    .containsOnly(
                        InformationRequestFieldKey.CUSTOMER to "Customer is missing",
                        InformationRequestFieldKey.ATTACHMENT to "Needs a letter of attorney",
                    )
            }
        }
    }
}

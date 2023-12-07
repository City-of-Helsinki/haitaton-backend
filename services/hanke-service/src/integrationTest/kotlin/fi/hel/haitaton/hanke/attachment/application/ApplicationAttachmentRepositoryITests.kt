package fi.hel.haitaton.hanke.attachment.application

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.test.Asserts.isSameInstantAs
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles

private const val BLOB_LOCATION = "1/bcae2ff2-74e9-48d2-a8ed-e33a40652304"

@ActiveProfiles("test")
@SpringBootTest
class ApplicationAttachmentRepositoryITests : DatabaseTest() {

    @Autowired private lateinit var alluDataFactory: AlluDataFactory
    @Autowired private lateinit var hankeFactory: HankeFactory
    @Autowired private lateinit var applicationAttachmentRepository: ApplicationAttachmentRepository

    @NullSource
    @ValueSource(strings = [BLOB_LOCATION])
    @ParameterizedTest
    fun `Should save and find hanke attachment with nullable blob location`(blobLocation: String?) {
        val hanke = hankeFactory.saveMinimal()
        val application = alluDataFactory.saveApplicationEntity("User", hanke)
        val saved =
            applicationAttachmentRepository.save(
                ApplicationAttachmentFactory.createEntity(
                    applicationId = application.id!!,
                    blobLocation = blobLocation,
                )
            )

        val attachments = applicationAttachmentRepository.findAll()

        assertThat(attachments).hasSize(1)
        assertThat(attachments.first()).all {
            prop(ApplicationAttachmentEntity::id).isNotNull().isEqualTo(saved.id)
            prop(ApplicationAttachmentEntity::fileName)
                .isEqualTo(ApplicationAttachmentFactory.FILE_NAME)
            prop(ApplicationAttachmentEntity::contentType)
                .isEqualTo(MediaType.APPLICATION_PDF_VALUE)
            prop(ApplicationAttachmentEntity::createdByUserId).isEqualTo(USERNAME)
            prop(ApplicationAttachmentEntity::createdAt)
                .isSameInstantAs(ApplicationAttachmentFactory.CREATED_AT)
            prop(ApplicationAttachmentEntity::applicationId).isEqualTo(application.id)
            prop(ApplicationAttachmentEntity::blobLocation).isEqualTo(blobLocation)
        }
    }
}

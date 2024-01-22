package fi.hel.haitaton.hanke.attachment.application

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.attachment.DEFAULT_SIZE
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.test.Asserts.isSameInstantAs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest
class ApplicationAttachmentRepositoryITests : DatabaseTest() {

    @Autowired private lateinit var applicationFactory: ApplicationFactory
    @Autowired private lateinit var hankeFactory: HankeFactory
    @Autowired private lateinit var applicationAttachmentRepository: ApplicationAttachmentRepository

    @Test
    fun `Should save and find hanke attachment with nullable blob location`() {
        val hanke = hankeFactory.saveMinimal()
        val application = applicationFactory.saveApplicationEntity("User", hanke)
        val saved =
            applicationAttachmentRepository.save(
                ApplicationAttachmentFactory.createEntity(
                    applicationId = application.id!!,
                )
            )

        val attachments = applicationAttachmentRepository.findAll()

        assertThat(attachments).single().all {
            prop(ApplicationAttachmentEntity::id).isNotNull().isEqualTo(saved.id)
            prop(ApplicationAttachmentEntity::fileName)
                .isEqualTo(ApplicationAttachmentFactory.FILE_NAME)
            prop(ApplicationAttachmentEntity::contentType)
                .isEqualTo(MediaType.APPLICATION_PDF_VALUE)
            prop(ApplicationAttachmentEntity::size).isEqualTo(DEFAULT_SIZE)
            prop(ApplicationAttachmentEntity::createdByUserId).isEqualTo(USERNAME)
            prop(ApplicationAttachmentEntity::createdAt)
                .isSameInstantAs(ApplicationAttachmentFactory.CREATED_AT)
            prop(ApplicationAttachmentEntity::applicationId).isEqualTo(application.id)
            prop(ApplicationAttachmentEntity::blobLocation).isEqualTo(saved.blobLocation)
        }
    }
}

package fi.hel.haitaton.hanke.attachment.hanke

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.attachment.DEFAULT_SIZE
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.test.Asserts.isSameInstantAs
import fi.hel.haitaton.hanke.test.USERNAME
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest
class HankeAttachmentRepositoryITests : DatabaseTest() {

    @Autowired private lateinit var hankeFactory: HankeFactory
    @Autowired private lateinit var hankeAttachmentRepository: HankeAttachmentRepository

    @Test
    fun `Should save and find hanke attachment with blob location`() {
        val hanke = hankeFactory.saveMinimal()
        val attachment = newAttachment(hanke)
        val saved = hankeAttachmentRepository.save(attachment)

        val attachments = hankeAttachmentRepository.findAll()

        assertThat(attachments).single().all {
            prop(HankeAttachmentEntity::id).isNotNull().isEqualTo(saved.id)
            prop(HankeAttachmentEntity::fileName).isEqualTo(ApplicationAttachmentFactory.FILE_NAME)
            prop(HankeAttachmentEntity::contentType).isEqualTo(APPLICATION_PDF_VALUE)
            prop(HankeAttachmentEntity::size).isEqualTo(DEFAULT_SIZE)
            prop(HankeAttachmentEntity::createdByUserId).isEqualTo(USERNAME)
            prop(HankeAttachmentEntity::createdAt)
                .isSameInstantAs(HankeAttachmentFactory.CREATED_AT)
            prop(HankeAttachmentEntity::hanke).isEqualTo(hanke)
            prop(HankeAttachmentEntity::blobLocation).isEqualTo(attachment.blobLocation)
        }
    }

    fun newAttachment(hanke: HankeEntity) =
        HankeAttachmentFactory.createEntity(
            id = null,
            hanke = hanke,
            createdByUser = USERNAME,
        )
}

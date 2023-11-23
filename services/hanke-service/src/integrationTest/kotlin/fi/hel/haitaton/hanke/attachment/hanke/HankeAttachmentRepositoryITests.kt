package fi.hel.haitaton.hanke.attachment.hanke

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.factory.AttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.test.Asserts.isSameInstantAs
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.test.context.ActiveProfiles

/** Consists of [HankeEntity.id] and a UUID. */
private const val BLOB_LOCATION = "1/bcae2ff2-74e9-48d2-a8ed-e33a40652304"

@ActiveProfiles("test")
@SpringBootTest
class HankeAttachmentRepositoryITests : DatabaseTest() {

    @Autowired private lateinit var hankeFactory: HankeFactory
    @Autowired private lateinit var hankeAttachmentRepository: HankeAttachmentRepository

    @NullSource
    @ValueSource(strings = [BLOB_LOCATION])
    @ParameterizedTest
    fun `Should save and find hanke attachment with nullable blob location`(blobLocation: String?) {
        val hanke = hankeFactory.saveMinimal()
        val saved = hankeAttachmentRepository.save(newAttachment(hanke, blobLocation))

        val attachments = hankeAttachmentRepository.findAll()

        assertThat(attachments).hasSize(1)
        assertThat(attachments.first()).all {
            prop(HankeAttachmentEntity::id).isNotNull().isEqualTo(saved.id)
            prop(HankeAttachmentEntity::fileName).isEqualTo(AttachmentFactory.FILE_NAME)
            prop(HankeAttachmentEntity::contentType).isEqualTo(APPLICATION_PDF_VALUE)
            prop(HankeAttachmentEntity::createdByUserId).isEqualTo(USERNAME)
            prop(HankeAttachmentEntity::createdAt)
                .isSameInstantAs(HankeAttachmentFactory.CREATED_AT)
            prop(HankeAttachmentEntity::hanke).isEqualTo(hanke)
            prop(HankeAttachmentEntity::blobLocation).isEqualTo(blobLocation)
        }
    }

    fun newAttachment(hanke: HankeEntity, blobLocation: String?) =
        HankeAttachmentFactory.createEntity(
            id = null,
            hanke = hanke,
            createdByUser = USERNAME,
            blobLocation = blobLocation,
        )
}

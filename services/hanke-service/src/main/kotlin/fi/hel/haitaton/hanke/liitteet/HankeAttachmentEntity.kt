package fi.hel.haitaton.hanke.liitteet

import fi.hel.haitaton.hanke.HankeEntity
import java.time.LocalDateTime
import java.util.*
import javax.persistence.Basic
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.validation.constraints.NotNull
import org.hibernate.annotations.Type

@Entity
@Table(name = "attachment")
class HankeAttachmentEntity(
    @Id @GeneratedValue var id: UUID? = null,
    @Column(nullable = false) var name: String,
    @Lob
    @Type(type = "org.hibernate.type.BinaryType")
    @Basic(fetch = FetchType.LAZY)
    @NotNull
    var data: ByteArray,
    @Column(updatable = false, nullable = false) var username: String,
    @Column(updatable = false, nullable = false) var created: LocalDateTime,
    @ManyToOne(cascade = []) var hanke: HankeEntity,
    @Enumerated(EnumType.STRING) var tila: AttachmentTila = AttachmentTila.PENDING
) {
    fun toAttachment(): HankeAttachment {
        return HankeAttachment(
            id = id,
            name = name,
            created = created,
            tila = tila,
            hankeId = hanke.id,
            user = username,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HankeAttachmentEntity

        if (id != other.id) return false
        if (name != other.name) return false
        if (!data.contentEquals(other.data)) return false
        if (username != other.username) return false
        if (created != other.created) return false
        if (hanke != other.hanke) return false
        if (tila != other.tila) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + name.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + created.hashCode()
        result = 31 * result + hanke.hashCode()
        result = 31 * result + tila.hashCode()
        return result
    }
}

enum class AttachmentTila {
    PENDING,
    FAILED,
    OK
}

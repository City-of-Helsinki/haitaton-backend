package fi.hel.haitaton.hanke.muutosilmoitus

import fi.hel.haitaton.hanke.hakemus.YhteyshenkiloEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

@Entity
@Table(name = "muutosilmoituksen_yhteyshenkilo")
class MuutosilmoituksenYhteyshenkiloEntity(
    @Id override val id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "muutosilmoituksen_yhteystieto_id", nullable = false)
    var yhteystieto: MuutosilmoituksenYhteystietoEntity,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "hankekayttaja_id", nullable = false)
    override var hankekayttaja: HankekayttajaEntity,
    override var tilaaja: Boolean,
) : YhteyshenkiloEntity

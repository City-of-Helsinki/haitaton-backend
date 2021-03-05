package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankeEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import javax.persistence.Embedded
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "tormaystarkastelutulos")
class TormaystarkasteluTulosEntity {
    @Embedded
    var liikennehaitta: LiikennehaittaIndeksiType? = null
    var perus: Float? = null
    var pyoraily: Float? = null
    var joukkoliikenne: Float? = null

    var tila: TormaystarkasteluTulosTila? = null
    var createdAt: LocalDateTime? = null

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "hankeid")
    var hanke: HankeEntity? = null
}

interface TormaystarkasteluTulosRepository: JpaRepository<TormaystarkasteluTulosEntity, Int> {
    /**
     * Note, as HankeEntity has lazy relation to the related set of tormaystarkastelutulos
     * entities, if one has the Hanke entity already, no need to pick the id and use this.
     */
    fun findByHankeId(hankeId: Int): List<TormaystarkasteluTulosEntity>
}
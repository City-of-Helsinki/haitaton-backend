package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.getCurrentTimeUTCAsLocalTime
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
    var tilaChangedAt: LocalDateTime? = null
    var createdAt: LocalDateTime? = null

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "hankeid")
    var hanke: HankeEntity? = null

    // ========  functions  =======

    // NOTE: there is no way to validate already invalidated result;
    // instead, must recalculate the results and thus create a new result.
    fun invalidate() {
        tila = TormaystarkasteluTulosTila.EI_VOIMASSA
        tilaChangedAt = getCurrentTimeUTCAsLocalTime()
    }

    fun addToHanke(hankeEntity: HankeEntity) {
        if (hanke != null && hanke != hankeEntity) {
            throw IllegalStateException("This TormaystarkasteluTulosEntity is already connected with another Hanke.")
        }
        hankeEntity.addTormaystarkasteluTulos(this)
    }

    fun removeFromHanke() {
        if (hanke == null)
            return
        hanke!!.removeTormaystarkasteluTulos(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TormaystarkasteluTulosEntity) return false

        // For persisted entities, checking id match is enough
        if (id != null && id != other.id) return false

        // For non-persisted, have to compare almost everything
        if (hanke != null && hanke != other.hanke) return false
        if (liikennehaitta != other.liikennehaitta) return false
        if (perus != other.perus) return false
        if (pyoraily != other.pyoraily) return false
        if (joukkoliikenne != other.joukkoliikenne) return false
        if (tila != other.tila) return false

        // Ignoring differences in the timestamps on purpose.

        return true;
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + (hanke?.hashCode() ?: 0)
        result = 31 * result + (liikennehaitta?.hashCode() ?: 0)
        result = 31 * result + (perus?.hashCode() ?: 0)
        result = 31 * result + (pyoraily?.hashCode() ?: 0)
        result = 31 * result + (joukkoliikenne?.hashCode() ?: 0)
        result = 31 * result + (tila?.hashCode() ?: 0)
        return result
    }
}

interface TormaystarkasteluTulosRepository: JpaRepository<TormaystarkasteluTulosEntity, Int> {
    /**
     * Note, as HankeEntity has lazy relation to the related set of tormaystarkastelutulos
     * entities, if one has the Hanke entity already, no need to pick the id and use this.
     */
    fun findByHankeId(hankeId: Int): List<TormaystarkasteluTulosEntity>
}
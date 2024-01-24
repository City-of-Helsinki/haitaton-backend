package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "hankealue")
class HankealueEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) override var id: Int = 0,
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hankeid")
    var hanke: HankeEntity? = null,

    // NOTE: This should be changed to an entity
    // Refers to HankeGeometria.
    var geometriat: Int? = null,
    var haittaAlkuPvm: LocalDate? = null,
    var haittaLoppuPvm: LocalDate? = null,
    var kaistaHaitta: VaikutusAutoliikenteenKaistamaariin? = null,
    var kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus? = null,
    var meluHaitta: Meluhaitta? = null,
    var polyHaitta: Polyhaitta? = null,
    var tarinaHaitta: Tarinahaitta? = null,
    var nimi: String
) : HasId<Int> {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HankealueEntity

        if (id != other.id) return false
        if (hanke != other.hanke) return false
        if (haittaAlkuPvm != other.haittaAlkuPvm) return false
        if (haittaLoppuPvm != other.haittaLoppuPvm) return false
        if (kaistaHaitta != other.kaistaHaitta) return false
        if (kaistaPituusHaitta != other.kaistaPituusHaitta) return false
        if (meluHaitta != other.meluHaitta) return false
        if (polyHaitta != other.polyHaitta) return false
        if (tarinaHaitta != other.tarinaHaitta) return false
        if (nimi != other.nimi) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + (hanke?.hashCode() ?: 0)
        result = 31 * result + (haittaAlkuPvm?.hashCode() ?: 0)
        result = 31 * result + (haittaLoppuPvm?.hashCode() ?: 0)
        result = 31 * result + (kaistaHaitta?.hashCode() ?: 0)
        result = 31 * result + (kaistaPituusHaitta?.hashCode() ?: 0)
        result = 31 * result + (meluHaitta?.hashCode() ?: 0)
        result = 31 * result + (polyHaitta?.hashCode() ?: 0)
        result = 31 * result + (tarinaHaitta?.hashCode() ?: 0)
        result = 31 * result + nimi.hashCode()
        return result
    }
}

fun List<HankealueEntity>.geometriaIds(): Set<Int> = mapNotNull { it.geometriat }.toSet()

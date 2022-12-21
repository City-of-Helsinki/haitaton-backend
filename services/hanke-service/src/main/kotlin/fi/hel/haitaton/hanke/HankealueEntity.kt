package fi.hel.haitaton.hanke

import java.time.LocalDate
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "hankealue")
class HankealueEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Int? = null

    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "hankeid") var hanke: HankeEntity? = null

    // NOTE: This should be changed to an entity
    // Refers to HankeGeometria.
    var geometriat: Int? = null

    var haittaAlkuPvm: LocalDate? = null
    var haittaLoppuPvm: LocalDate? = null

    var kaistaHaitta: TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin? = null
    var kaistaPituusHaitta: KaistajarjestelynPituus? = null
    var meluHaitta: Haitta13? = null
    var polyHaitta: Haitta13? = null
    var tarinaHaitta: Haitta13? = null

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

        return true
    }

    override fun hashCode(): Int {
        var result = id ?: 0
        result = 31 * result + (hanke?.hashCode() ?: 0)
        result = 31 * result + (haittaAlkuPvm?.hashCode() ?: 0)
        result = 31 * result + (haittaLoppuPvm?.hashCode() ?: 0)
        result = 31 * result + (kaistaHaitta?.hashCode() ?: 0)
        result = 31 * result + (kaistaPituusHaitta?.hashCode() ?: 0)
        result = 31 * result + (meluHaitta?.hashCode() ?: 0)
        result = 31 * result + (polyHaitta?.hashCode() ?: 0)
        result = 31 * result + (tarinaHaitta?.hashCode() ?: 0)
        return result
    }
}

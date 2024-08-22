package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Haittojenhallintatyyppi
import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulosEntity
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.MapKeyEnumerated
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
    @Enumerated(EnumType.STRING) var kaistaHaitta: VaikutusAutoliikenteenKaistamaariin? = null,
    @Enumerated(EnumType.STRING)
    var kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus? = null,
    @Enumerated(EnumType.STRING) var meluHaitta: Meluhaitta? = null,
    @Enumerated(EnumType.STRING) var polyHaitta: Polyhaitta? = null,
    @Enumerated(EnumType.STRING) var tarinaHaitta: Tarinahaitta? = null,
    var nimi: String,
    // Made bidirectional relation mainly to allow cascaded delete.
    @OneToOne(
        fetch = FetchType.LAZY,
        mappedBy = "hankealue",
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    var tormaystarkasteluTulos: TormaystarkasteluTulosEntity?,
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "hankkeen_haittojenhallintasuunnitelma",
        joinColumns = [JoinColumn(name = "hankealue_id", referencedColumnName = "id")]
    )
    @MapKeyColumn(name = "tyyppi")
    @Column(name = "sisalto")
    @MapKeyEnumerated(EnumType.STRING)
    var haittojenhallintasuunnitelma: MutableMap<Haittojenhallintatyyppi, String> = mutableMapOf(),
) : HasId<Int> {
    fun haittaAjanKestoDays(): Int? =
        if (haittaAlkuPvm != null && haittaLoppuPvm != null) {
            ChronoUnit.DAYS.between(haittaAlkuPvm, haittaLoppuPvm).toInt() + 1
        } else {
            null
        }

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

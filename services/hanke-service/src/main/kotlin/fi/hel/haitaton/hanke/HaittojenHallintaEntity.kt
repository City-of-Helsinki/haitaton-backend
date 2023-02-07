package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.HaittojenHallinta
import fi.hel.haitaton.hanke.domain.HaittojenHallintaKentta
import fi.hel.haitaton.hanke.domain.HaittojenHallintaKuvaus
import javax.persistence.*
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "haittojenhallinta")
class HaittojenHallintaEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @ElementCollection
    @CollectionTable(
        name = "haittojenhallinta_kuvaukset",
        joinColumns = [JoinColumn(name = "haittojenhallinta_id", referencedColumnName = "id")]
    )
    @MapKeyJoinColumn(name = "key")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "value")
    var kuvaukset: MutableMap<HaittojenHallintaKentta, String?> = mutableMapOf(),
) {

    companion object {
        fun toEntity(source: HaittojenHallinta?, target: HaittojenHallintaEntity) {
            if (source == null) {
                return
            }

            target.kuvaukset.clear()
            source.kuvaukset.forEach {
                if (it.value.kaytossaHankkeessa) {
                    target.kuvaukset[it.key] = it.value.kuvaus
                }
            }
        }

        fun toDto(source: HaittojenHallintaEntity?, target: HaittojenHallinta) {
            if (source == null) {
                return
            }
            HaittojenHallintaKentta.values().forEach {
                target.kuvaukset[it] =
                    if (source.kuvaukset.contains(it))
                        HaittojenHallintaKuvaus(true, source.kuvaukset[it] ?: "")
                    else HaittojenHallintaKuvaus(false, "")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HaittojenHallintaEntity

        if (id != other.id) return false
        if (kuvaukset != other.kuvaukset) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + kuvaukset.hashCode()
        return result
    }
}

interface HaittojenHallintaRepository : JpaRepository<HaittojenHallintaEntity, Long> {}

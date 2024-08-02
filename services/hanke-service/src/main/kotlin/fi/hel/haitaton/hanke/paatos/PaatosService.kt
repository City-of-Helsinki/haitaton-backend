package fi.hel.haitaton.hanke.paatos

import org.springframework.stereotype.Service

@Service
class PaatosService(
    private val paatosRepository: PaatosRepository,
) {
    fun findByHakemusId(hakemusId: Long): List<Paatos> =
        paatosRepository.findByHakemusId(hakemusId).map { it.toDomain() }
}

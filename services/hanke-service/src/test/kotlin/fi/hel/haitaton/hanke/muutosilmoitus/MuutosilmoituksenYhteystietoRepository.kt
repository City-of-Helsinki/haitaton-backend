package fi.hel.haitaton.hanke.muutosilmoitus

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface MuutosilmoituksenYhteystietoRepository :
    JpaRepository<MuutosilmoituksenYhteystietoEntity, UUID>

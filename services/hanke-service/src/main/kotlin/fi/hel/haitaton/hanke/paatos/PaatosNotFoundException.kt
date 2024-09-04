package fi.hel.haitaton.hanke.paatos

import java.util.UUID

class PaatosNotFoundException(paatosId: UUID) :
    RuntimeException("Paatos not found with id $paatosId")

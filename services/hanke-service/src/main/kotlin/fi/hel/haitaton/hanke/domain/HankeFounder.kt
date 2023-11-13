package fi.hel.haitaton.hanke.domain

/**
 * HankeFounder used to define a person for whom an initial HankeKayttaja instance is created for
 * when creating a Hanke.
 */
data class HankeFounder(val name: String, val email: String)

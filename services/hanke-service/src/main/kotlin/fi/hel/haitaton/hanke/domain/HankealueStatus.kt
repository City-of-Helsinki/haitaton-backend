package fi.hel.haitaton.hanke.domain

enum class HankealueStatus {
    /**
     * A hankealue is in draft status when it has validation errors (e.g., missing
     * haittojenhallintasuunnitelma).
     */
    DRAFT,

    /** A hankealue is public when it has all required data and passes validation. */
    PUBLIC,
}

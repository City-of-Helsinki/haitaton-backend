package fi.hel.haitaton.hanke.domain

/**
 * Marker interface for classes that have an id-field of type [ID]. Useful for generic functions
 * that need access to the id-field.
 */
interface HasId<ID> {
    val id: ID
}

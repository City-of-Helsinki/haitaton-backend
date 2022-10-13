package fi.hel.haitaton.hanke.factory

abstract class Factory<T> {

    /**
     * Mutates the target of this factory. Can be used to avoid breaking factory method chains.
     *
     * Example:
     * ```
     * listOf(
     *     HankeFactory.create().withHaitta().mutate {
     *         it.tormaystarkasteluTulos = TormaystarkasteluTulos(1f, 1f, 1f)
     *     },
     *     HankeFactory.create(id = 444, hankeTunnus = "HAI-TEST-2").withHaitta()
     * )
     * ```
     */
    fun T.mutate(mutator: (T) -> Unit): T {
        mutator(this)
        return this
    }
}

package fi.hel.haitaton.hanke.gdpr

abstract class Node {
    abstract val key: String
}

data class StringNode(override val key: String, val value: String) : Node()

data class IntNode(override val key: String, val value: Int) : Node()

data class CollectionNode(override val key: String, val children: List<Node>) : Node()

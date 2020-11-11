package fi.hel.haitaton.hanke

fun Any.toJsonString(): String {
    return OBJECT_MAPPER.writeValueAsString(this)
}

fun Any.toJsonPrettyString(): String {
    return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this)
}
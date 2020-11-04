package fi.hel.haitaton.hanke

fun Any.toJsonString(): String {
    return objectMapper.writeValueAsString(this)
}

fun Any.toJsonPrettyString(): String {
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
}
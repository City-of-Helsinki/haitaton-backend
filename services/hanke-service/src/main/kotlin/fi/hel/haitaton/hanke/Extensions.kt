package fi.hel.haitaton.hanke

fun Any?.toJsonString(): String = OBJECT_MAPPER.writeValueAsString(this)

fun Any?.toJsonPrettyString(): String = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this)

fun <T> String.asJsonResource(type: Class<T>): T = OBJECT_MAPPER.readValue(type.getResource(this).readText(Charsets.UTF_8), type)
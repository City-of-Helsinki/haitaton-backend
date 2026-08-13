package fi.hel.haitaton.hanke.configuration

import fi.hel.haitaton.hanke.OBJECT_MAPPER
import io.hypersistence.utils.hibernate.type.util.JsonSerializer
import io.hypersistence.utils.hibernate.type.util.JsonSerializerSupplier
import io.hypersistence.utils.hibernate.type.util.ObjectMapperJsonSerializer
import io.hypersistence.utils.hibernate.type.util.ObjectMapperSupplier
import io.hypersistence.utils.hibernate.type.util.ObjectMapperWrapper
import org.geojson.LngLatAlt
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule

/**
 * hypersistence-utils 3.15's default [ObjectMapperJsonSerializer] clones JSON-mapped entity
 * attributes via Java serialization, which requires every mapped type (and its whole object graph,
 * including third-party types such as org.geojson's classes) to implement [java.io.Serializable].
 * Cloning via Jackson instead avoids that requirement for our own domain types, matching the
 * pre-3.15 behavior. This same [JsonSerializer.clone] is also invoked internally by
 * hypersistence-utils on JDBC-level types such as [org.postgresql.util.PGobject], which already
 * implement Serializable and round-trip correctly via the default cloner but not via a generic
 * Jackson bean conversion - those are left untouched. Registered via
 * `hypersistence-utils.properties`.
 */
class ObjectMapperCloningJsonSerializer : JsonSerializer {
    private val default = ObjectMapperJsonSerializer()

    override fun <T> clone(value: T): T {
        if (value == null) return value
        if (value is java.io.Serializable) return default.clone(value)
        @Suppress("UNCHECKED_CAST")
        return OBJECT_MAPPER.convertValue(value, value.javaClass) as T
    }
}

class ObjectMapperCloningJsonSerializerSupplier : JsonSerializerSupplier {
    override fun get(): JsonSerializer = ObjectMapperCloningJsonSerializer()
}

/**
 * de.grundid.opendatalab:geojson-jackson's [LngLatAlt] serializes as a flat GeoJSON position array
 * (`[lon, lat, alt?]`) via `@JsonSerialize`/`@JsonDeserialize(using = ...)` referencing Jackson 2's
 * `com.fasterxml.jackson.databind` annotations. Spring Boot 4's hypersistence-utils uses Jackson 3
 * internally for JSON columns, which doesn't recognize those Jackson-2-typed annotations, silently
 * falling back to default bean introspection and corrupting the array shape (Postgres then rejects
 * it: "coordinates in GeoJSON are not sufficiently nested"). These re-implement the same array
 * format for Jackson 3. Registered via `hypersistence-utils.properties`.
 */
class LngLatAltJackson3Serializer : ValueSerializer<LngLatAlt>() {
    override fun serialize(value: LngLatAlt, gen: JsonGenerator, ctxt: SerializationContext) {
        gen.writeStartArray()
        gen.writeNumber(value.longitude)
        gen.writeNumber(value.latitude)
        if (value.hasAltitude()) {
            gen.writeNumber(value.altitude)
        }
        value.additionalElements?.forEach { gen.writeNumber(it) }
        gen.writeEndArray()
    }
}

class LngLatAltJackson3Deserializer : ValueDeserializer<LngLatAlt>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LngLatAlt {
        val values = mutableListOf<Double>()
        var token = p.nextToken()
        while (token != null && token != JsonToken.END_ARRAY) {
            values.add(p.doubleValue)
            token = p.nextToken()
        }
        if (values.size < 2) {
            return ctxt.reportInputMismatch(
                LngLatAlt::class.java,
                "GeoJSON position must have at least 2 coordinates (longitude, latitude), got %d",
                values.size,
            )
        }
        val result = LngLatAlt()
        result.longitude = values[0]
        result.latitude = values[1]
        values.getOrNull(2)?.let { result.altitude = it }
        if (values.size > 3) {
            result.setAdditionalElements(*values.drop(3).toDoubleArray())
        }
        return result
    }
}

class GeoJsonAwareObjectMapperSupplier : ObjectMapperSupplier {
    override fun get(): tools.jackson.databind.ObjectMapper {
        val mapper =
            ObjectMapperWrapper.INSTANCE.objectMapper
                .rebuild<JsonMapper, JsonMapper.Builder>()
                .addModule(
                    SimpleModule()
                        .addSerializer(LngLatAlt::class.java, LngLatAltJackson3Serializer())
                        .addDeserializer(LngLatAlt::class.java, LngLatAltJackson3Deserializer())
                )
                .build()
        // hypersistence-utils also reads/writes JSON via the static ObjectMapperWrapper.INSTANCE
        // singleton directly (e.g. for Hibernate's dirty-checking) rather than always going
        // through this ObjectMapperSupplier - keep both in sync or the geojson fix above is
        // silently bypassed on that path.
        ObjectMapperWrapper.INSTANCE.objectMapper = mapper
        return mapper
    }
}

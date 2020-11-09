package fi.hel.haitaton.hanke

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

class HankeErrorSerializer : StdSerializer<HankeError>(HankeError::class.java) {
    override fun serialize(value: HankeError?, gen: JsonGenerator, provider: SerializerProvider) {
        if (value != null) {
            gen.writeStartObject()
            gen.writeStringField("errorCode", value.errorCode)
            gen.writeStringField("errorMessage", value.errorMessage)
            gen.writeEndObject()
        }
    }
}
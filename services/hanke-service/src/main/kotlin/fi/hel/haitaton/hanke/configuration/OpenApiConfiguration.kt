package fi.hel.haitaton.hanke

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.servers.Server

@OpenAPIDefinition(
    info =
        Info(
            title = "Haitaton Internal API",
            description = "API for Haitaton internal use. Can change without warning.",
            version = "1"
        ),
    servers = [Server(url = "/")]
)
internal class OpenAPIConfiguration

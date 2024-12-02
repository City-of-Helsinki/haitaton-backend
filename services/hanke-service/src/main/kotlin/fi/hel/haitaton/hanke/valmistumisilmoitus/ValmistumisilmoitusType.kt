package fi.hel.haitaton.hanke.valmistumisilmoitus

enum class ValmistumisilmoitusType(val urlSuffix: String, val logName: String) {
    TOIMINNALLINEN_KUNTO("operationalcondition", "operational condition"),
    TYO_VALMIS("workfinished", "work finished"),
}

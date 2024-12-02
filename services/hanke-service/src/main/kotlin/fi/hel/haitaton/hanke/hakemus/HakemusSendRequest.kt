package fi.hel.haitaton.hanke.hakemus

data class HakemusSendRequest(val paperDecisionReceiver: PaperDecisionReceiver?)

data class PaperDecisionReceiver(
    val name: String,
    val streetAddress: String,
    val postalCode: String,
    val city: String,
)

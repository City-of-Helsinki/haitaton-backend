package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.hakemus.PaperDecisionReceiver

object PaperDecisionReceiverFactory {
    val default =
        PaperDecisionReceiver(
            "Pekka Paperinen",
            "Paperipolku 3 A 4",
            "00451",
            "Helsinki",
        )
}

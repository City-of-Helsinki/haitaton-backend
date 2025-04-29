package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.muutosilmoitus.MuutosilmoitusAttachmentMetadataDto
import fi.hel.haitaton.hanke.banners.BannerResponse
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.PublicHanke
import fi.hel.haitaton.hanke.gdpr.CollectionNode
import fi.hel.haitaton.hanke.hakemus.HakemusDeletionResultDto
import fi.hel.haitaton.hanke.hakemus.HakemusResponse
import fi.hel.haitaton.hanke.hakemus.HakemusWithExtrasResponse
import fi.hel.haitaton.hanke.hakemus.HankkeenHakemuksetResponse
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusResponse
import fi.hel.haitaton.hanke.permissions.HankeKayttajaController
import fi.hel.haitaton.hanke.permissions.HankeKayttajaDto
import fi.hel.haitaton.hanke.permissions.HankeKayttajaResponse
import fi.hel.haitaton.hanke.permissions.HankekayttajaDeleteService
import fi.hel.haitaton.hanke.permissions.WhoamiResponse
import fi.hel.haitaton.hanke.profiili.Names
import fi.hel.haitaton.hanke.taydennys.TaydennysResponse
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import kotlin.reflect.KClass
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

@Aspect
@Component
class DisclosureLoggingAspect(private val disclosureLogService: DisclosureLogService) {
    @AfterReturning(
        pointcut = "@annotation(io.swagger.v3.oas.annotations.Operation)",
        returning = "result",
    )
    fun logResponse(result: Any?) {
        if (result == null) {
            return
        }
        logResult(result)
    }

    private fun logResult(result: Any) {
        when (result) {
            // For types that (can) contain personal information, log said information
            is CollectionNode -> disclosureLogService.saveForProfiili(result, currentUserId())
            is HakemusResponse ->
                disclosureLogService.saveForHakemusResponse(result, currentUserId())
            is HakemusWithExtrasResponse -> {
                disclosureLogService.saveForHakemusResponse(result.hakemus, currentUserId())
                result.taydennys?.let {
                    disclosureLogService.saveForTaydennys(it.taydennys, currentUserId())
                }
                result.muutosilmoitus?.let {
                    disclosureLogService.saveForMuutosilmoitus(it.muutosilmoitus, currentUserId())
                }
            }
            is Hanke -> disclosureLogService.saveForHanke(result, currentUserId())
            is HankeKayttajaDto ->
                disclosureLogService.saveForHankeKayttaja(result, currentUserId())
            is HankeKayttajaResponse ->
                disclosureLogService.saveForHankeKayttajat(result.kayttajat, currentUserId())
            is MuutosilmoitusResponse ->
                disclosureLogService.saveForMuutosilmoitus(result, currentUserId())
            is Names -> disclosureLogService.saveForProfiiliNimi(result, currentUserId())
            is TaydennysResponse -> disclosureLogService.saveForTaydennys(result, currentUserId())

            // Some classes cannot hold personal information, so they are skipped
            is ApplicationAttachmentMetadataDto -> return
            is TaydennysAttachmentMetadataDto -> return
            is MuutosilmoitusAttachmentMetadataDto -> return
            is HakemusDeletionResultDto -> return
            is HankeAttachmentMetadataDto -> return
            is HankeKayttajaController.TunnistautuminenResponse -> return
            is HankekayttajaDeleteService.DeleteInfo -> return
            is HankkeenHakemuksetResponse -> return
            is TormaystarkasteluTulos -> return
            is WhoamiResponse -> return

            // We can't know if binary data contains personal information. These need to be handled
            // in the respective controllers.
            is ByteArray -> return

            // Content is extracted from wrapper types and any non-null content is evaluated
            is ResponseEntity<*> -> result.body?.let { logResult(it) }
            is List<*> -> logResultList(result.filterNotNull())
            is Map<*, *> -> logResultList(result.values.filterNotNull())

            // Used for returning system info. Won't contain personal information.
            is String -> return

            // Throw an exception if nothing matches. This will ensure we specify whether a new
            // response type can contain personal information or not.
            else -> throw UnknownResponseTypeException(result::class)
        }
    }

    private fun logResultList(results: List<Any>) {
        if (results.isEmpty()) return
        when (val first = results.first()) {
            is Hanke -> {
                val hankkeet = checkAllElementsAreOfClass<Hanke>(results)
                disclosureLogService.saveForHankkeet(hankkeet, currentUserId())
            }

            is ApplicationAttachmentMetadataDto -> return
            is BannerResponse -> return
            is HankeAttachmentMetadataDto -> return
            is PublicHanke -> return
            is String -> return
            is WhoamiResponse -> return

            else -> throw UnknownResponseListTypeException(first::class)
        }
    }

    private inline fun <reified T> checkAllElementsAreOfClass(list: List<Any>): List<T> {
        if (list.all { it is T }) {
            return list.filterIsInstance<T>()
        } else {
            throw MixedElementsInResponseException(T::class, list.map { it::class }.toSet())
        }
    }
}

class UnknownResponseTypeException(kclass: KClass<*>) :
    RuntimeException("Unknown response type: ${kclass.qualifiedName}")

class UnknownResponseListTypeException(kclass: KClass<*>) :
    RuntimeException("Unknown response type inside a list: ${kclass.qualifiedName}")

class MixedElementsInResponseException(expected: KClass<*>, actual: Set<KClass<*>>) :
    RuntimeException(
        "Mixed types inside a list. Expected type: ${expected.qualifiedName} Actual types: ${actual.map { it.qualifiedName }.joinToString ()}"
    )

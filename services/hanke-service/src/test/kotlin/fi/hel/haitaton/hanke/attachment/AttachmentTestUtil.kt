package fi.hel.haitaton.hanke.attachment

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.attachment.common.FileResult
import fi.hel.haitaton.hanke.attachment.common.FileScanData
import fi.hel.haitaton.hanke.attachment.common.FileScanResponse
import fi.hel.haitaton.hanke.getResourceAsBytes
import fi.hel.haitaton.hanke.hankeError
import fi.hel.haitaton.hanke.toJsonString
import mockwebserver3.MockResponse
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

const val FILE_NAME_PDF = "file.pdf"
const val FILE_PARAM = "liite"
const val HANKE_TUNNUS = "HAI-1234"
const val APPLICATION_ID = 1L
const val CONTENT_TYPE_HEADER = "Content-Type"

val DUMMY_DATA = "ABC".toByteArray()
val PDF_BYTES by lazy { "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes() }
val DEFAULT_SIZE by lazy { PDF_BYTES.size.toLong() }

fun testFile(
    fileParam: String = FILE_PARAM,
    fileName: String = FILE_NAME_PDF,
    contentType: String? = APPLICATION_PDF_VALUE,
    data: ByteArray = PDF_BYTES,
) = MockMultipartFile(fileParam, fileName, contentType, data)

fun ResultActions.andExpectError(error: HankeError): ResultActions =
    andExpect(unauthenticated()).andExpect(status().isUnauthorized).andExpect(hankeError(error))

fun response(data: String): MockResponse =
    MockResponse.Builder()
        .body(data)
        .addHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON_VALUE)
        .code(200)
        .build()

fun body(success: Boolean = true, results: List<FileResult>): String =
    FileScanResponse(success, FileScanData(result = results)).toJsonString()

fun failResult(): List<FileResult> =
    listOf(FileResult(name = FILE_NAME_PDF, isInfected = true, viruses = listOf("virus")))

fun successResult(): List<FileResult> =
    listOf(FileResult(name = FILE_NAME_PDF, isInfected = false, viruses = emptyList()))

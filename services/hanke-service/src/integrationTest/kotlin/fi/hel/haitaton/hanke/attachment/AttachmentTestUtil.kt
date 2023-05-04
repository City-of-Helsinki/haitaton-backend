package fi.hel.haitaton.hanke.attachment

import fi.hel.haitaton.hanke.HankeError
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

const val USERNAME = "username"
const val FILE_NAME_PDF = "file.pdf"
const val FILE_PARAM = "liite"
const val HANKE_TUNNUS = "HAI-1234"
const val HANKE_ID = 123
const val APPLICATION_ID = 1L

val dummyData = "ABC".toByteArray()

fun testFile(
    fileParam: String = FILE_PARAM,
    fileName: String = FILE_NAME_PDF,
    contentType: String = APPLICATION_PDF_VALUE,
    data: ByteArray = dummyData,
) = MockMultipartFile(fileParam, fileName, contentType, data)

fun ResultActions.andExpectError(error: HankeError): ResultActions =
    andExpect(unauthenticated())
        .andExpect(status().isUnauthorized)
        .andExpect(jsonPath("$.errorCode").value(error.errorCode))
        .andExpect(jsonPath("$.errorMessage").value(error.errorMessage))

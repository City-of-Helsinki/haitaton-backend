package fi.hel.haitaton.hanke.allu

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import io.mockk.every
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@WithMockUser
class ApplicationServiceTest {

    @MockkBean private lateinit var applicationRepo: ApplicationRepository

    @MockkBean private lateinit var cableReportService: CableReportService

    @Test
    fun test() {
        val service = ApplicationService(applicationRepo, cableReportService)

        val json =
            """
        {
            "name":"test",
            "customerWithContacts":{
                "customer":{
                    "type":"PERSON",
                    "name":"Testi Testinen",
                    "country":"FI",
                    "postalAddress":{
                        "streetAddress":{
                            "streetName":"Mannerheimintie 1 2 3"
                        },
                        "postalCode":"00900",
                        "city":"Helsinki"
                    },
                    "email":"test@haitaton.fi",
                    "phone":"0123456789",
                    "registryKey":null,
                    "ovt":null,
                    "invoicingOperator":null,
                    "sapCustomerNumber":null
                },
                "contacts":[
                    {
                        "email":"test@haitaton.fi",
                        "name":"Testi Testinen",
                        "orderer":true,
                        "phone":"0123456789",
                        "postalAddress":{
                            "city":"Helsinki",
                            "postalCode":"00900",
                            "streetAddress":{
                                "streetName":"Mannerheimintie 1 2 3"
                            }
                        }
                    }
                ]
            },
            "geometry":{
                "type":"GeometryCollection",
                "crs":{
                    "type":"name",
                    "properties":{
                        "name":"EPSG:3879"
                    }
                },
                "geometries":[
                    {
                        "type":"Polygon",
                        "coordinates":[
                            [
                                [ 25497314.94, 6672065.27 ],
                                [ 25497232.87, 6672189.43 ],
                                [ 25497108.71, 6672107.36 ],
                                [ 25497190.78, 6671983.2 ],
                                [ 25497314.94, 6672065.27 ]
                            ]
                        ]
                    }
                ]
            },
            "startTime":"2022-06-01T00:00:00Z",
            "endTime":"2022-07-01T00:00:00Z",
            "pendingOnClient":true,
            "identificationNumber":"HAI-123",
            "clientApplicationKind":"Testi",
            "workDescription":"anything goes",
            "contractorWithContacts":{
                "customer":{
                    "type":"COMPANY",
                    "name":"Haitaton Oy",
                    "country":"FI",
                    "postalAddress":{
                        "streetAddress":{
                            "streetName":"Mannerheimintie 1 2 3"
                        },
                        "postalCode":"00900",
                        "city":"Helsinki"
                    },
                    "email":"test@haitaton.fi",
                    "phone":"0123456789",
                    "registryKey":null,
                    "ovt":null,
                    "invoicingOperator":null,
                    "sapCustomerNumber":null
                },
                "contacts":[
                    {
                        "name":"Testi Testinen",
                        "postalAddress":{
                            "streetAddress":{
                                "streetName":"Mannerheimintie 1 2 3"
                            },
                            "postalCode":"00900",
                            "city":"Helsinki"
                        },
                        "email":"test@haitaton.fi",
                        "phone":"0123456789",
                        "orderer":false
                    }
                ]
            },
            "postalAddress":null,
            "representativeWithContacts":null,
            "invoicingCustomer":null,
            "customerReference":null,
            "area":null,
            "propertyDeveloperWithContacts":null,
            "constructionWork":false,
            "maintenanceWork":false,
            "emergencyWork":true,
            "propertyConnectivity":false
        }
        """.trimIndent()

        val applicationData = OBJECT_MAPPER.readTree(json)
        val dto =
            ApplicationDto(
                id = null,
                alluid = null,
                applicationType = ApplicationType.CABLE_REPORT,
                applicationData = applicationData
            )

        every { cableReportService.create(any()) } returns 42
        every { applicationRepo.save(any()) } answers
            {
                val application = firstArg<AlluApplication>()
                AlluApplication(
                    id = 1,
                    alluid = application.alluid,
                    userId = application.userId,
                    applicationType = application.applicationType,
                    applicationData = application.applicationData
                )
            }

        val created = service.create(dto)
        assertThat(created.id).isEqualTo(1)
        assertThat(created.alluid).isEqualTo(42)
    }
}

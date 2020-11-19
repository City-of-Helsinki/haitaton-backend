package fi.hel.haitaton.hanke.domain

data class ContactInformation {

    var surname: String
    var firstname: String

}

//e.g. omistaja, arvioija, toteuttaja


data class ContactOrganization(
        var id: String,
        var name: String
)

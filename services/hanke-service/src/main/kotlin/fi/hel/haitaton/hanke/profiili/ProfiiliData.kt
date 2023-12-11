package fi.hel.haitaton.hanke.profiili

data class ProfiiliResponse(val data: ProfiiliData)

data class ProfiiliData(val myProfile: MyProfile?)

data class MyProfile(val verifiedPersonalInformation: Names?)

data class Names(val firstName: String, val lastName: String, val givenName: String)

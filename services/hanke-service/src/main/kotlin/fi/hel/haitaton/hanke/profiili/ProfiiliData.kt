package fi.hel.haitaton.hanke.profiili

data class ProfiiliResponse(val data: ProfiiliData)

data class ProfiiliData(val myProfile: MyProfile?)

data class MyProfile(val verifiedPersonalInformation: Names?)

data class Names(
    /** All the first names of the person. Like "Matti Tapani". */
    val firstName: String,
    val lastName: String,
    /**
     * The name that's selected as a preferred forename (kutsumanimi) in DVV. This can be any of the
     * official first names of that person. So for "Matti Tapani" this would be "Matti" or "Tapani".
     * For "Sirkka-Liisa", this can be either "Sirkka", "Liisa" or "Sirkka-Liisa".
     */
    val givenName: String
)

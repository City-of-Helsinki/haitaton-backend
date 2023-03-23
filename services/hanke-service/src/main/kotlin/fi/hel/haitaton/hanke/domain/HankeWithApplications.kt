package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.application.Application

data class HankeWithApplications(val hanke: Hanke, val applications: List<Application>)

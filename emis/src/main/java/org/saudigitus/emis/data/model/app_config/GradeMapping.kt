package org.saudigitus.emis.data.model.app_config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class GradeMapping(
    @JsonProperty("gradeOptionSet")
    val gradeOptionSet: String?,
    @JsonProperty("ranges")
    val ranges: List<GradeRange>?
)

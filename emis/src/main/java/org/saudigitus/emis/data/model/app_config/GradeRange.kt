package org.saudigitus.emis.data.model.app_config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class GradeRange(
    @JsonProperty("optionCode")
    val optionCode: String,
    @JsonProperty("minScore")
    val minScore: Double,
    @JsonProperty("maxScore")
    val maxScore: Double
)

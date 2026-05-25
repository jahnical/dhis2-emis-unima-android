package org.saudigitus.emis.data.model.app_config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TermRemarkRange(
    @JsonProperty("optionCode")
    val optionCode: String,
    @JsonProperty("minPercentage")
    val minPercentage: Double,
    @JsonProperty("maxPercentage")
    val maxPercentage: Double
)

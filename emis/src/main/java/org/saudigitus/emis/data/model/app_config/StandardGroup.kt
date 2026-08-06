package org.saudigitus.emis.data.model.app_config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class StandardGroup(
    @JsonProperty("optionCode")
    val optionCode: String,
    @JsonProperty("standards")
    val standards: List<String>?,
    @JsonProperty("subjects")
    val subjects: List<String>?
)

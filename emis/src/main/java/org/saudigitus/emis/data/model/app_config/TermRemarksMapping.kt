package org.saudigitus.emis.data.model.app_config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TermRemarksMapping(
    @JsonProperty("dataElement")
    val dataElement: String?,
    @JsonProperty("optionSet")
    val optionSet: String?,
    @JsonProperty("ranges")
    val ranges: List<TermRemarkRange>?
)

package org.saudigitus.emis.data.model.app_config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SubjectMapping(
    @JsonProperty("scoreDataElement")
    val scoreDataElement: String,
    @JsonProperty("gradeDataElement")
    val gradeDataElement: String,
    @JsonProperty("universal")
    val universal: Boolean?
)

package org.saudigitus.emis.data.model.app_config


import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class Performance(
    @JsonProperty("enabled")
    val enabled: Boolean?,
    @JsonProperty("lastUpdate")
    val lastUpdate: String?,
    @JsonProperty("programStages")
    val programStages: List<ProgramStages?>?,
    @JsonProperty("subjects")
    val subjects: List<SubjectMapping>?,
    @JsonProperty("gradeMapping")
    val gradeMapping: GradeMapping?
)
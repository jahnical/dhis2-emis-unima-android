package org.saudigitus.emis.data.model.app_config


import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class Transfer(
    @JsonProperty("approvedCode")
    val approvedCode: String?,
    @JsonProperty("destinySchool")
    val destinySchool: String?,
    @JsonProperty("enabled")
    val enabled: Boolean?,
    @JsonProperty("lastUpdate")
    val lastUpdate: String?,
    @JsonProperty("originSchool")
    val originSchool: String?,
    @JsonProperty("penddingCode")
    val penddingCode: String?,
    @JsonProperty("programStage")
    val programStage: String?,
    @JsonProperty("reason")
    val reason: String?,
    @JsonProperty("reprovedCode")
    val reprovedCode: String?,
    @JsonProperty("status")
    val status: String?,
    @JsonProperty("statusOptions")
    val statusOptions: List<StatusOption>?
)
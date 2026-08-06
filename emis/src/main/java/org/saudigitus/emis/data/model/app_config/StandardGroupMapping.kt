package org.saudigitus.emis.data.model.app_config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class StandardGroupMapping(
    @JsonProperty("standardGroupOptionSet")
    val standardGroupOptionSet: String?,
    @JsonProperty("groups")
    val groups: List<StandardGroup>?
)
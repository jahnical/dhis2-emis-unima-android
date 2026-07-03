package org.unima.emis.data.model

import org.saudigitus.emis.data.model.app_config.EMISConfig
import org.saudigitus.emis.data.model.app_config.EMISConfigItem
import timber.log.Timber

/**
 * UNIMA-specific override of EMISConfig with enhanced error logging.
 * Wraps the base [EMISConfig.fromJson] and adds detailed diagnostics on failure.
 */
object UnimaEMISConfig {

    fun fromJson(json: String?): List<EMISConfigItem>? {
        if (json == null) {
            Timber.tag("UNIMA_CONFIG").w("fromJson called with null JSON")
            return null
        }

        val result = EMISConfig.fromJson(json)

        if (result == null) {
            val jsonPreview = if (json.length > 200) {
                json.substring(0, 200) + "..."
            } else {
                json
            }
            Timber.tag("UNIMA_CONFIG").e(
                "EMISConfig parsing returned null.\n" +
                    "JSON length: ${json.length}\n" +
                    "JSON preview: $jsonPreview",
            )
        } else {
            Timber.tag("UNIMA_CONFIG").d(
                "EMISConfig parsed successfully: ${result.size} items, " +
                    "programs: ${result.map { it.program }}",
            )
        }

        return result
    }
}

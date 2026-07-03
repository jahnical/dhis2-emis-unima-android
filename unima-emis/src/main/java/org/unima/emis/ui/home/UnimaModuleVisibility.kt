package org.unima.emis.ui.home

import org.saudigitus.emis.data.model.Module
import org.saudigitus.emis.data.model.app_config.EMISConfigItem
import org.saudigitus.emis.utils.Constants

/**
 * UNIMA-specific module visibility logic.
 *
 * Unlike the base EMIS which requires `enabled: true` in each feature config,
 * UNIMA determines visibility based on whether the feature section exists
 * in the configuration at all. This allows features to be shown as long
 * as they are configured, without needing an explicit `enabled` flag.
 *
 * Usage: Call [getModules] from your ViewModel's setProgram() to get
 * the UNIMA-specific module list.
 */
object UnimaModuleVisibility {

    /**
     * Returns a list of modules with display state based on whether
     * the corresponding configuration section is present (not null).
     *
     * Base EMIS checks: config?.attendance?.enabled ?: false
     * UNIMA checks:     config?.attendance != null
     */
    fun getModules(config: EMISConfigItem?): List<Module> {
        return listOf(
            Module(
                key = Constants.ABSENTEEISM,
                display = config?.absenteeism != null,
            ),
            Module(
                key = Constants.ATTENDANCE,
                display = config?.attendance != null,
            ),
            Module(
                key = Constants.PERFORMANCE,
                display = config?.performance != null,
            ),
        )
    }
}


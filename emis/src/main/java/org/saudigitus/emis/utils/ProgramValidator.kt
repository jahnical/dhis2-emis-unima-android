package org.saudigitus.emis.utils

import org.hisp.dhis.android.core.D2
import org.saudigitus.emis.data.model.app_config.EMISConfig
import timber.log.Timber
import javax.inject.Inject

class ProgramValidator @Inject constructor(private val d2: D2) {
    fun isSEMIS(program: String): Boolean {
        if (program.isBlank()) return false
        return try {
            val dataStore = d2.dataStoreModule()
                .dataStore()
                .byNamespace().eq("semis")
                .byKey().eq(Constants.KEY)
                .one().blockingGet()
                ?: d2.dataStoreModule()
                    .dataStore()
                    .byNamespace().eq("SEMIS")
                    .byKey().eq(Constants.KEY)
                    .one().blockingGet()

            val json = dataStore?.value()
            val config = EMISConfig.fromJson(json) ?: emptyList()
            val isConfigured = config.any { it.program == program || it.key == program }
            Timber.tag("EMIS_WIRING").d(
                "SEMIS config check programUid=%s datastoreFound=%s configCount=%s matched=%s",
                program,
                dataStore != null,
                config.size,
                isConfigured,
            )
            isConfigured
        } catch (e: Exception) {
            Timber.e(e, "Error checking if program $program is SEMIS")
            false
        }
    }
}
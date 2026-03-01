package org.unima.emis.utils

/**
 * UNIMA-specific constants that extend or override base EMIS constants.
 * Keep UNIMA-specific configuration values here so they don't pollute the base module.
 */
object UnimaConstants {
    // UNIMA DataStore namespace
    const val NAMESPACE = "semis"

    // UNIMA-specific feature flags
    const val ENROLLMENT = "enrollment"
    const val TRANSFER = "transfer"
    const val FINAL_RESULT = "final-result"
    const val SOCIO_ECONOMICS = "socio-economics"

    // Logging tags
    const val LOG_TAG_CONFIG = "UNIMA_CONFIG"
    const val LOG_TAG_ABSENTEEISM = "UNIMA_ABSENTEEISM"
    const val LOG_TAG_ATTENDANCE = "UNIMA_ATTENDANCE"
}


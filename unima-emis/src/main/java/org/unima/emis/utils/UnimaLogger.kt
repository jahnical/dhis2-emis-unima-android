package org.unima.emis.utils

import timber.log.Timber

/**
 * UNIMA-specific logging utility.
 * Provides tagged, structured logging for debugging UNIMA customizations.
 */
class UnimaLogger {

    fun logConfig(tag: String, message: String) {
        Timber.tag("UNIMA_$tag").d(message)
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Timber.tag("UNIMA_$tag").e(throwable, message)
        } else {
            Timber.tag("UNIMA_$tag").e(message)
        }
    }

    fun logAbsenteeismQuery(
        ou: String,
        program: String,
        stage: String,
        attendanceStage: String,
        attendanceDataElement: String,
        date: String?,
        dataElementIds: List<String>,
        options: List<String>,
        resultCount: Int,
    ) {
        val tag = "UNIMA_ABSENTEEISM"
        Timber.tag(tag).d("geTeiByAttendanceStatus called")
        Timber.tag(tag).d("ou='$ou', program='$program', stage='$stage'")
        Timber.tag(tag).d("attendanceStage='$attendanceStage', attendanceDataElement='$attendanceDataElement'")
        Timber.tag(tag).d("date='$date', dataElementIds=$dataElementIds")
        Timber.tag(tag).d("options=$options")
        Timber.tag(tag).d("Query returned $resultCount results")
    }

    fun logConfigParsing(json: String?, exception: Exception) {
        val jsonPreview = if (json != null && json.length > 200) {
            json.substring(0, 200) + "..."
        } else {
            json ?: "null"
        }
        Timber.tag("UNIMA_CONFIG").e(
            exception,
            "Failed to parse EMISConfig from JSON.\n" +
                "Error type: ${exception::class.simpleName}\n" +
                "Error message: ${exception.message}\n" +
                "JSON length: ${json?.length ?: 0}\n" +
                "JSON preview: $jsonPreview",
        )
    }
}


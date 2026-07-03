package org.unima.emis.data

import org.saudigitus.emis.data.model.SearchTeiModel
import org.saudigitus.emis.data.model.dto.AttendanceEntity
import timber.log.Timber

/**
 * UNIMA-specific debug logging helpers for DataManager operations.
 *
 * Use these methods to add debug logging around base EMIS data operations
 * without modifying the base code.
 */
object UnimaDataManagerDebug {

    fun logAbsenteeismQuery(
        ou: String,
        program: String,
        stage: String,
        attendanceStage: String,
        attendanceDataElement: String,
        reasonDataElement: String,
        date: String?,
        dataElementIds: List<String>,
        options: List<String>,
    ) {
        val tag = "UNIMA_ABSENTEEISM"
        Timber.tag(tag).d("geTeiByAttendanceStatus called")
        Timber.tag(tag).d("ou='$ou', program='$program', stage='$stage'")
        Timber.tag(tag).d("attendanceStage='$attendanceStage', attendanceDataElement='$attendanceDataElement'")
        Timber.tag(tag).d("date='$date', dataElementIds=$dataElementIds")
        Timber.tag(tag).d("options=$options")
    }

    fun logAbsenteeismResult(result: Map<SearchTeiModel, AttendanceEntity>) {
        Timber.tag("UNIMA_ABSENTEEISM").d("Query returned ${result.size} results")
    }
}

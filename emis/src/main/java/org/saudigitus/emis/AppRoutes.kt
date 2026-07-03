package org.saudigitus.emis

object AppRoutes {
    const val HOME_ROUTE = "HOME_ROUTE"
    const val TEI_LIST_ROUTE = "TEI_LIST_ROUTE"
    const val ATTENDANCE_ROUTE = "ATTENDANCE_ROUTE"
    const val ABSENTEEISM_ROUTE = "ABSENTEEISM_ROUTE"
    const val PERFORMANCE_ROUTE = "PERFORMANCE_ROUTE"
    const val SUBJECT_ROUTE = "SUBJECT_ROUTE"
    const val STUDENT_SUMMARY_ROUTE = "STUDENT_SUMMARY_ROUTE"

    fun studentSummaryRoute(ou: String, stage: String, tei: String, studentName: String) =
        "$STUDENT_SUMMARY_ROUTE/$ou/$stage/$tei/$studentName"

    fun absenteeismRoute(
        academicYear: String?,
        school: String?,
        grade: String?,
        section: String?,
    ) = "${ABSENTEEISM_ROUTE}/$school/$academicYear/$grade/$section"
}

package org.saudigitus.emis.utils

object Constants {
    // data store
    const val KEY = "values"
    const val CALENDAR_KEY = "schoolCalendar"
    const val STAFF = "staff"
    const val STUDENT = "student"

    const val ATTENDANCE = "attendance"
    const val ABSENTEEISM = "absenteeism"
    const val PERFORMANCE = "performance"

    // attr
    const val DEFAULT = "default"

    // attendance status
    const val ABSENT = "absent"

    //
    const val DEFAULT_COLOR = "#F81C784"

    const val ANALYTICS_TEI = "ANALYTICS_TEI"
    const val ANALYTICS_PROGRAM = "ANALYTICS_PROGRAM"
    const val OWNER_ORG_UNIT = "OWNER_ORG_UNIT"
    const val ACADEMIC_YEAR = "ACADEMIC_YEAR"
    const val TRACKER_NAME = "TRACKER_NAME"

    const val CARD_VALUE = "CARD_VALUE"
    const val SINGLE_VALUE = "SINGLE_VALUE"

    /**
     * When true: only DEs with an explicIt core mapping are shown (strict/configured-only-mode
     * When false: all non-grade DEs are shown (permissive/all-inclusive-mode)
     * */
    const val CONFIGURED_SUBJECT_FILTERING = true
}

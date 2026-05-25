package org.saudigitus.emis.data.model

data class SubjectResult(
    val subjectUid: String,
    val subjectName: String,
    val score: String?,
    val gradeCode: String?,
    val gradeDisplayName: String?,
)

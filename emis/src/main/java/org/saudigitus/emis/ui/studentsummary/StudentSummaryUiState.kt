package org.saudigitus.emis.ui.studentsummary

import org.saudigitus.emis.data.model.SearchTeiModel
import org.saudigitus.emis.data.model.SubjectResult
import org.saudigitus.emis.ui.components.ToolbarHeaders

data class StudentSummaryUiState(
    val toolbarHeaders: ToolbarHeaders,
    val results: List<SubjectResult> = emptyList(),
    val students: List<SearchTeiModel> = emptyList(),
    val selectedTei: String = "",
)
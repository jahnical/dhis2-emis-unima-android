package org.saudigitus.emis.ui.subjects

import org.saudigitus.emis.data.model.SearchTeiModel
import org.saudigitus.emis.data.model.Subject
import org.saudigitus.emis.ui.components.DropdownItem
import org.saudigitus.emis.ui.components.ToolbarHeaders

enum class SubjectTab { SUBJECTS, STUDENTS }

data class SubjectUIState(
    val toolbarHeaders: ToolbarHeaders,
    val filters: List<DropdownItem> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val selectedTab: SubjectTab = SubjectTab.SUBJECTS,
    val students: List<SearchTeiModel> = emptyList(),
)

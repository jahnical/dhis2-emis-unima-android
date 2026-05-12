package org.saudigitus.emis.ui.performance

import org.saudigitus.emis.data.model.SearchTeiModel
import org.saudigitus.emis.data.model.Subject
import org.saudigitus.emis.ui.components.ToolbarHeaders
import org.saudigitus.emis.ui.form.Field
import org.saudigitus.emis.ui.form.FormData
import org.saudigitus.emis.ui.form.FormField

data class PerformanceUiState(
    val toolbarHeaders: ToolbarHeaders,
    val students: List<SearchTeiModel> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val fieldsState: List<Field> = emptyList(),
    val formFields: List<FormField> = emptyList(),
    val formData: List<FormData>? = emptyList(),
    val isValidating: Boolean ,
    // list of dataElement uids that should be rendered read-only (e.g. grade DEs)
    val readOnlyFields: List<String> = emptyList(),
)

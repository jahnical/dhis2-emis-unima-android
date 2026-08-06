package org.saudigitus.emis.ui.studentsummary

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.saudigitus.emis.data.local.DataManager
import org.saudigitus.emis.ui.base.BaseViewModel
import org.saudigitus.emis.ui.components.ToolbarHeaders
import javax.inject.Inject

@HiltViewModel
class StudentSummaryViewModel
@Inject constructor(
    private val repository: DataManager,
) : BaseViewModel(repository) {

    private val _uiState = MutableStateFlow(
        StudentSummaryUiState(toolbarHeaders = ToolbarHeaders(title = "")),
    )
    val uiState: StateFlow<StudentSummaryUiState> = _uiState

    private val _stage = MutableStateFlow("")

    init {
        viewModelScope.launch {
            teis.collect { list ->
                _uiState.update { it.copy(students = list) }
            }
        }
    }

    override fun setConfig(program: String) {}

    override fun setProgram(program: String) {
        _program.value = program
    }

    override fun setDate(date: String) {}

    override fun save() {}

    fun setStage(stage: String) {
        _stage.value = stage
    }

    fun setSelectedStudent(tei: String, name: String) {
        _uiState.update {
            it.copy(
                selectedTei = tei,
                toolbarHeaders = it.toolbarHeaders.copy(title = name),
            )
        }
        loadResults(tei)
    }

    private fun loadResults(tei: String) {
        viewModelScope.launch {
            val enrollment = teiUIds.value.find { it.first == tei }?.second.orEmpty()
            val results = repository.getStudentSubjectResults(
                tei = tei,
                program = program.value,
                stage = _stage.value,
                enrollment = enrollment,
                grade = grade.value,
            )
            val termSummary = repository.computeAndSaveTermSummary(
                tei = tei,
                program = program.value,
                stage = _stage.value,
                enrollment = enrollment,
                results = results,
            )
            _uiState.update {
                it.copy(
                    results = results,
                    totalScore = termSummary?.totalScore,
                    termRemark = termSummary?.termRemarkDisplayName,
                )
            }
        }
    }
}
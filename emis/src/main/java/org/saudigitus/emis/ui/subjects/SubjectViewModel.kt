package org.saudigitus.emis.ui.subjects

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.saudigitus.emis.data.local.DataManager
import org.saudigitus.emis.data.model.app_config.Performance
import org.saudigitus.emis.ui.base.BaseViewModel
import org.saudigitus.emis.ui.components.ToolbarHeaders
import org.saudigitus.emis.utils.Constants
import org.saudigitus.emis.utils.subjectsForGrade
import javax.inject.Inject

@HiltViewModel
class SubjectViewModel
@Inject constructor(
    private val repository: DataManager,
) : BaseViewModel(repository) {

    private val _uiState = MutableStateFlow(
        SubjectUIState(ToolbarHeaders(title = "Subjects")),
    )
    val uiState: StateFlow<SubjectUIState> = _uiState

    private val _programStage = MutableStateFlow("")
    val programStage: StateFlow<String> = _programStage

    private val gradeDEUids = mutableSetOf<String>()
    private val scoreDeUids = mutableSetOf<String>()
    private var gradeOptionSetUid: String? = null
    private var performanceConfig: Performance? = null

    init {
        viewModelScope.launch {
            teis.collect { list ->
                _uiState.update { it.copy(students = list) }
            }
        }
    }

    override fun setConfig(program: String) {
        viewModelScope.launch {
            val config = repository.getConfig(Constants.KEY)?.find { it.program == program }

            if (config?.performance != null) {
                performanceConfig = config.performance
                gradeDEUids.clear()
                gradeDEUids.addAll(
                    config.performance.subjects?.map { it.gradeDataElement } ?: emptyList()
                )
                scoreDeUids.clear()
                scoreDeUids.addAll(
                    config.performance.subjects?.map { it.scoreDataElement } ?: emptyList()
                )
                gradeOptionSetUid = config.performance.gradeMapping?.gradeOptionSet

                val stages = config.performance.programStages
                    ?.filterNotNull()
                    ?: emptyList()

                _uiState.update {
                    it.copy(filters = repository.getTerms(stages))
                }

                val selected = uiState.value.filters.getOrNull(0)

                if (selected != null) {
                    performOnFilterClick(selected.id)
                }
            }
        }
    }

    override fun setProgram(program: String) {
        setConfig(program)
    }

    override fun setDate(date: String) {}

    override fun save() {}

    fun onTabSelected(tab: SubjectTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun performOnFilterClick(stage: String) {
        _programStage.value = stage
        viewModelScope.launch {
            val all = repository.getSubjects(stage)
            val filtered = if (Constants.CONFIGURED_SUBJECT_FILTERING) {
                all.filter { it.uid in scoreDeUids }
            } else {
                all.filter { de ->
                    (gradeOptionSetUid.isNullOrEmpty() || de.optionSetUid != gradeOptionSetUid) &&
                        de.uid !in gradeDEUids
                }
            }

            val groupSubjectUids = performanceConfig?.subjectsForGrade(grade.value)
            val bySubjectGroup = if (groupSubjectUids != null) {
                filtered.filter { it.uid in groupSubjectUids }
            } else {
                filtered
            }

            _uiState.update { it.copy(subjects = bySubjectGroup) }
        }
    }
}

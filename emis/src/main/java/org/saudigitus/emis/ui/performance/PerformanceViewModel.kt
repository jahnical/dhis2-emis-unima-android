package org.saudigitus.emis.ui.performance

import android.util.Log
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.dhis2.commons.date.DateUtils
import org.dhis2.form.model.ActionType
import org.dhis2.form.model.RowAction
import org.hisp.dhis.android.core.common.ValueType
import org.hisp.dhis.android.core.program.ProgramRuleActionType
import org.saudigitus.emis.data.local.DataManager
import org.saudigitus.emis.data.local.FormRepository
import org.saudigitus.emis.data.model.EventTuple
import org.saudigitus.emis.data.model.app_config.GradeRange
import org.saudigitus.emis.service.RuleEngineRepository
import org.saudigitus.emis.utils.Constants
import org.saudigitus.emis.ui.attendance.ButtonStep
import org.saudigitus.emis.ui.base.BaseViewModel
import org.saudigitus.emis.ui.form.Field
import org.saudigitus.emis.utils.DateHelper
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PerformanceViewModel
@Inject constructor(
    private val repository: DataManager,
    private val formRepository: FormRepository,
    private val ruleRepository: RuleEngineRepository,
) : BaseViewModel(repository) {

    private val viewModelState = MutableStateFlow(
        PerformanceUiState(
            toolbarHeaders = this.toolbarHeaders.value,
            students = this.teis.value,
            isValidating = false
        ),
    )

    val uiState = viewModelState
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            viewModelState.value,
        )

    private val _cache = MutableStateFlow<List<EventTuple>>(emptyList())
    val cache: StateFlow<List<EventTuple>> = _cache

    private val _programStage = MutableStateFlow("")
    private val programStage: StateFlow<String> = _programStage

    private val _dataElement = MutableStateFlow("")
    private val dataElement: StateFlow<String> = _dataElement

    private val _saveOnce = MutableStateFlow(0)
    private val saveOnce: StateFlow<Int> = _saveOnce

    // mapping: score dataElement uid -> grade dataElement uid (populated from config)
    private val _subjectGradeMap = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _gradeRanges = MutableStateFlow<List<GradeRange>>(emptyList())

    private val fieldValidationJobs = mutableMapOf<String, Job>()

    init {
        _toolbarHeaders.update {
            it.copy(
                title = "Performance",
                subtitle = null,
            )
        }
        viewModelState.update {
            it.copy(toolbarHeaders = this.toolbarHeaders.value)
        }
    }

    override fun setConfig(program: String) {}

    override fun setProgram(program: String) {
        _program.value = program
    }

    private fun resolveGradeCode(score: Double): String? =
        _gradeRanges.value.firstOrNull { score >= it.minScore && score <= it.maxScore }?.optionCode

    fun loadSubjects(stage: String) {
        viewModelScope.launch {
            val performance = repository.getConfig(Constants.KEY)
                ?.find { it.program == program.value }
                ?.performance

            val configSubjects = performance?.subjects ?: emptyList()
            _subjectGradeMap.value = configSubjects.associate { it.scoreDataElement to it.gradeDataElement }
            _gradeRanges.value = performance?.gradeMapping?.ranges ?: emptyList()

            val scoreDeUids = configSubjects.map { it.scoreDataElement }.toSet()
            val allDEs = repository.getSubjects(stage)
            val subjects = if (scoreDeUids.isNotEmpty()) allDEs.filter { it.uid in scoreDeUids } else allDEs

            viewModelState.update { it.copy(subjects = subjects) }
        }
    }

    override fun setDate(date: String) {
        _toolbarHeaders.update {
            it.copy(subtitle = DateHelper.formatDateWithWeekDay(date))
        }
        viewModelState.update {
            it.copy(toolbarHeaders = toolbarHeaders.value)
        }
    }

    override fun save() {
        viewModelScope.launch {
            when (buttonStep.value) {
                ButtonStep.EDITING -> {
                    setButtonStep(ButtonStep.HOLD_SAVING)
                }
                ButtonStep.HOLD_SAVING -> {
                    setButtonStep(ButtonStep.SAVING)
                }
                else -> {
                    if (!viewModelState.value.isValidating) {
                        cache.value.forEach { item ->
                            formRepository.save(item)
                        }

                        _cache.value = emptyList()
                        setButtonStep(ButtonStep.EDITING)
                    }
                }
            }
        }
    }

    fun onClickNext(
        tei: String,
        ou: String,
        fieldData: Triple<String, String?, ValueType?>,
    ) {
        val data = mutableListOf<EventTuple>()
        data.addAll(cache.value)

        val eventTuple = EventTuple(
            ou,
            program.value,
            programStage.value,
            tei,
            RowAction(
                id = dataElement.value.ifEmpty { fieldData.first },
                type = ActionType.ON_NEXT,
                value = fieldData.second,
                valueType = fieldData.third,
            ),
            eventDate.value,
        )

        data.removeIf { it.tei == tei }

        data.add(eventTuple)

        _cache.value = data
        viewModelState.update { it.copy(isValidating = false) }
    }

    private fun getFields(stage: String, dl: String) {
        viewModelScope.launch {
            _programStage.value = stage

            val baseFields = formRepository.keyboardInputTypeByStage(program.value, stage, dl)
            val gradeDl = _subjectGradeMap.value[dl]
            val allFields = if (!gradeDl.isNullOrEmpty()) {
                val gradeFields = formRepository.keyboardInputTypeByStage(program.value, stage, gradeDl)
                (baseFields + gradeFields).distinctBy { it.uid }
            } else {
                baseFields
            }

            // mark readOnly fields (grade DEs)
            val readOnly = gradeDl?.let { listOf(it) } ?: emptyList()

            viewModelState.update {
                it.copy(formFields = allFields, readOnlyFields = readOnly)
            }
        }
    }

    fun setDefault(
        stage: String,
        dl: String,
    ) {
        if (saveOnce.value == 0) {
            _saveOnce.value = 1
            getFields(stage, dl)
            updateDataFields(dl)
        }
    }

    fun updateDataFields(dl: String) {
        getFields(programStage.value, dl)
        viewModelScope.launch {
            _dataElement.value = dl
            val gradeDl = _subjectGradeMap.value[dl]

            val baseFlow = formRepository.getEvents(
                ou = ou.value,
                program = program.value,
                programStage = programStage.value,
                dataElement = dl,
                teis = teiUIds.value.map { it.first },
            )

            if (gradeDl.isNullOrEmpty()) {
                baseFlow.conflate()
                    .distinctUntilChanged()
                    .collectLatest { events ->
                        Log.e("EVENTS", "$events")
                        viewModelState.update {
                            it.copy(formData = events)
                        }
                    }
            } else {
                val gradeFlow = formRepository.getEvents(
                    ou = ou.value,
                    program = program.value,
                    programStage = programStage.value,
                    dataElement = gradeDl,
                    teis = teiUIds.value.map { it.first },
                )

                combine(baseFlow, gradeFlow) { baseList, gradeList ->
                    val merged = (baseList + gradeList).distinctBy { it.tei + it.dataElement }
                    merged
                }
                    .conflate()
                    .distinctUntilChanged()
                    .collectLatest { events ->
                        Log.e("EVENTS", "$events")
                        viewModelState.update {
                            it.copy(formData = events)
                        }
                    }
            }
        }
    }

    fun updateTEISList() {
        viewModelState.update {
            it.copy(students = this.teis.value)
        }
    }

    fun fieldState(
        key: String,
        event: String,
        dataElement: String,
        value: String,
        valueType: ValueType?,
    ) {
        val currentFields = viewModelState.value.fieldsState.toMutableList()
        val index = currentFields.indexOfFirst { it.key == key && it.dataElement == dataElement }

        val field = Field(
            key = key,
            event = event,
            dataElement = dataElement,
            value = value,
            valueType = valueType,
            hasError = false,
            errorMessage = null,
        )

        if (index >= 0) {
            currentFields[index] = field
        } else {
            currentFields.add(field)
        }

        viewModelState.update { it.copy(fieldsState = currentFields) }

        // Checkpoint 1: confirm fieldState was called and inputs look sane
        Timber.tag("RULE_ENGINE").d(
            "fieldState called: de=%s event=%s ou=%s program=%s stage=%s value=%s",
            dataElement, event, ou.value, program.value, programStage.value, value
        )

        val jobKey = "$key:$dataElement"
        fieldValidationJobs[jobKey]?.cancel()
        viewModelState.update { it.copy(isValidating = true) }

        fieldValidationJobs[jobKey] = viewModelScope.launch {
            try {
                // Config-based grade resolution (replaces ASSIGN rule action)
                val gradeDeUid = _subjectGradeMap.value[dataElement]
                if (!gradeDeUid.isNullOrEmpty()) {
                    val score = value.toDoubleOrNull()
                    val gradeCode = if (score != null) resolveGradeCode(score) else null
                    val resolvedValue = gradeCode ?: ""

                    val gradeField = Field(
                        key = key, event = event, dataElement = gradeDeUid,
                        value = resolvedValue, valueType = null, hasError = false, errorMessage = null,
                    )
                    val gradeFields = viewModelState.value.fieldsState.toMutableList()
                    val gradeIdx = gradeFields.indexOfFirst { it.key == key && it.dataElement == gradeDeUid }
                    if (gradeIdx >= 0) gradeFields[gradeIdx] = gradeField else gradeFields.add(gradeField)
                    viewModelState.update { it.copy(fieldsState = gradeFields) }

                    val updatedCache = _cache.value.toMutableList()
                    updatedCache.removeIf { it.tei == key && it.rowAction.id == gradeDeUid }
                    updatedCache.add(
                        EventTuple(
                            ou = ou.value, program = program.value,
                            programStage = programStage.value, tei = key,
                            rowAction = RowAction(
                                id = gradeDeUid, type = ActionType.ON_NEXT,
                                value = resolvedValue, valueType = null,
                            ),
                            date = eventDate.value,
                        )
                    )
                    _cache.value = updatedCache

                    Timber.tag("RULE_ENGINE").d(
                        "Grade resolved from config: score=%s grade=%s de=%s", value, resolvedValue, gradeDeUid
                    )
                }

                // Checkpoint 2: confirm the coroutine started
                Timber.tag("RULE_ENGINE").d("Calling evaluateDataEntryEffects for de=%s", dataElement)

                val effects = ruleRepository.evaluateDataEntryEffects(
                    ou = ou.value,
                    program = program.value,
                    stage = programStage.value,
                    dataElement = dataElement,
                    event = event,
                    eventDate = DateHelper.formatDate(DateUtils.getInstance().today.time).orEmpty(),
                    value = value,
                )

                Timber.tag("RULE_ENGINE").d(
                    "Effects for de=%s key=%s count=%d", dataElement, key, effects.size
                )

                var errorMessage: String? = null

                effects.forEach { effect ->
                    val action = effect.ruleAction ?: run {
                        Timber.tag("RULE_ENGINE").w("Skipping effect with null ruleAction")
                        return@forEach
                    }
                    val actionValues = action.values ?: emptyMap()
                    val actionType = action.type ?: run {
                        Timber.tag("RULE_ENGINE").w("Skipping effect with null action type")
                        return@forEach
                    }

                    Timber.tag("RULE_ENGINE").d(
                        "Effect type=%s data=%s values=%s", actionType, effect.data, actionValues
                    )

                    when (actionType) {
                        ProgramRuleActionType.SHOWERROR.name -> {
                            val content = actionValues["content"] ?: effect.data
                            if (!content.isNullOrBlank()) errorMessage = content
                        }

                        ProgramRuleActionType.ASSIGN.name -> {
                            // Deprecated: grade assignment now handled by config-based resolveGradeCode()
                            Timber.tag("RULE_ENGINE").d("ASSIGN rule action skipped (deprecated)")
                        }

                        else -> {
                            Timber.tag("RULE_ENGINE").d("Unhandled effect type: %s", actionType)
                        }
                    }
                }

                val updatedFields = viewModelState.value.fieldsState.toMutableList()
                val idx =
                    updatedFields.indexOfFirst { it.key == key && it.dataElement == dataElement }

                if (idx >= 0) {
                    val validatedField = updatedFields[idx].copy(
                        hasError = errorMessage != null,
                        errorMessage = errorMessage,
                    )
                    updatedFields[idx] = validatedField
                    viewModelState.update { it.copy(fieldsState = updatedFields) }
                }

                val stillValidating = fieldValidationJobs.any { (k, job) -> k != jobKey && job.isActive }
                viewModelState.update { it.copy(isValidating = stillValidating) }
            } catch (e: Exception) {
                Timber.tag("RULE_ENGINE").e(
                    e, "evaluateDataEntryEffects failed: de=%s event=%s ou=%s program=%s stage=%s",
                    dataElement, event, ou.value, program.value, programStage.value
                )
                viewModelState.update { it.copy(isValidating = false) }
            }
        }
    }

    private suspend fun validateDataEntry(
        event: String,
        value: String,
    ): String? {
        val effect = ruleRepository.evaluateDataEntry(
            ou = ou.value,
            program = program.value,
            stage = programStage.value,
            dataElement = dataElement.value,
            event = event,
            eventDate = DateHelper.formatDate(DateUtils.getInstance().today.time).orEmpty(),
            value = value,
        )
        return effect?.ruleAction?.values["content"]
    }
}

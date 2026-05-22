package org.saudigitus.emis.ui.attendance

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.dhis2.commons.date.DateUtils
import org.hisp.dhis.android.core.common.ValueType
import org.saudigitus.emis.data.local.DataManager
import org.saudigitus.emis.data.local.FormRepository
import org.saudigitus.emis.data.model.app_config.Attendance
import org.saudigitus.emis.data.model.Summary
import org.saudigitus.emis.data.model.dto.Absence
import org.saudigitus.emis.data.model.dto.AttendanceEntity
import org.saudigitus.emis.ui.base.BaseViewModel
import org.saudigitus.emis.ui.form.Field
import org.saudigitus.emis.ui.form.FormData
import org.saudigitus.emis.ui.form.FormField
import org.saudigitus.emis.utils.Constants.KEY
import org.saudigitus.emis.utils.DateHelper
import org.saudigitus.emis.utils.Utils.WHITE
import org.saudigitus.emis.utils.getOption
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AttendanceViewModel
@Inject constructor(
    private val repository: DataManager,
    private val formRepository: FormRepository,
) : BaseViewModel(repository) {

    private val _datastoreAttendance = MutableStateFlow<Attendance?>(null)
    private val datastoreAttendance: StateFlow<Attendance?> = _datastoreAttendance

    private val _attendanceOptions = MutableStateFlow<List<AttendanceOption>>(emptyList())
    val attendanceOptions: StateFlow<List<AttendanceOption>> = _attendanceOptions

    private val _attendanceStatus = MutableStateFlow<List<AttendanceEntity>>(emptyList())
    val attendanceStatus: StateFlow<List<AttendanceEntity>> = _attendanceStatus

    private val _attendanceStep = MutableStateFlow(ButtonStep.EDITING)
    val attendanceStep: StateFlow<ButtonStep> = _attendanceStep

    private val _attendanceBtnState =
        MutableStateFlow<List<AttendanceActionButtonState>>(emptyList())
    val attendanceBtnState: StateFlow<List<AttendanceActionButtonState>> = _attendanceBtnState

    private val attendanceCache = mutableSetOf<AttendanceEntity>()
    private var attendanceBtnStateCache = mutableListOf(AttendanceActionButtonState())

    private val _absenceState = MutableStateFlow(Absence())
    private val absenceState: StateFlow<Absence> = _absenceState

    private val _absenceStateCache = MutableStateFlow<List<Absence>>(emptyList())
    val absenceStateCache: StateFlow<List<Absence>> = _absenceStateCache

    private val _formFields = MutableStateFlow<List<FormField>>(emptyList())
    val formFields: StateFlow<List<FormField>> = _formFields

    private val _fieldState = MutableStateFlow<List<Field>>(emptyList())
    val fieldState: StateFlow<List<Field>> = _fieldState

    private val _formData = MutableStateFlow<List<FormData>>(emptyList())
    val formData: StateFlow<List<FormData>> = _formData

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isOnlyAbsence = MutableStateFlow(false)
    private val isOnlyAbsence: StateFlow<Boolean> = _isOnlyAbsence

    private val _options = MutableStateFlow<List<String>>(emptyList())
    private val options: StateFlow<List<String>> = _options

    fun setDefaults(title: String, onlyAbsence: Boolean = false) {
        _toolbarHeaders.update {
            it.copy(
                title = title,
            )
        }
        _isOnlyAbsence.value = onlyAbsence
    }

    fun setOptions(academicYear: String, grade: String, section: String) {
        _options.value = listOf(academicYear, grade, section)
    }

    override fun setConfig(program: String) {
        viewModelScope.launch {
            val config = repository.getConfig(KEY)?.find { it.program == program }

            if (config != null) {
                _datastoreAttendance.value = config.attendance
            }
            getAttendanceOptions(program)
            getFields(
                datastoreAttendance.value?.programStage.orEmpty(),
                datastoreAttendance.value?.absenceReason.orEmpty(),
            )
        }
    }

    override fun setProgram(program: String) {
        _program.value = program

        setConfig(program)

        if (isOnlyAbsence.value) {
            geTeiByAttendanceStatus()
        } else {
            attendanceEvents()
        }
    }

    fun setAttendanceStep(attendanceStep: ButtonStep) {
        _attendanceStep.value = attendanceStep
    }

    override fun setDate(date: String) {
        _eventDate.value = date
        if (isOnlyAbsence.value) {
            geTeiByAttendanceStatus(date)
        } else {
            attendanceEvents(date)
        }
        _toolbarHeaders.update {
            it.copy(
                subtitle = DateHelper.formatDateWithWeekDay(date),
            )
        }
    }

    private suspend fun getAttendanceOptions(
        program: String,
    ) {
        _attendanceOptions.value = repository.getAttendanceOptions(program)
    }

    private fun getFields(stage: String, dl: String) {
        viewModelScope.launch {
            _formFields.value = formRepository.keyboardInputTypeByStage(program.value,stage, dl)
        }
    }

    private fun attendanceEvents(
        date: String? = DateHelper.formatDate(DateUtils.getInstance().today.time),
    ) {
        viewModelScope.launch {
            _attendanceBtnState.value = emptyList()
            _attendanceStatus.value = emptyList()

            try {
                _attendanceStatus.value = async {
                    repository.getAttendanceEvent(
                        program = program.value,
                        programStage = datastoreAttendance.value?.programStage.orEmpty(),
                        dataElement = datastoreAttendance.value?.status.orEmpty(),
                        reasonDataElement = datastoreAttendance.value?.absenceReason.orEmpty(),
                        teis = teiUIds.value.map { it.first },
                        date = date.toString(),
                    )
                }.await()

                clearCache()
                attendanceCache.addAll(attendanceStatus.value)

                setInitialAttendanceStatus()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Timber.tag("ATTENDANCE_DATA").d("Coroutine cancelled: ${e.message}")
                    throw e
                }
                Timber.tag("ATTENDANCE_DATA").e(e)
            }
        }
    }

    private fun geTeiByAttendanceStatus(
        date: String? = DateHelper.formatDate(DateUtils.getInstance().today.time),
    ) {
        viewModelScope.launch {
            _attendanceBtnState.value = emptyList()
            _isLoading.value = true
            val config = repository.getConfig(KEY)?.find { it.program == program.value }
            val registration = config?.registration

            val response = async {
                repository.geTeiByAttendanceStatus(
                    ou = ou.value,
                    program = program.value,
                    stage = registration?.programStage.orEmpty(),
                    attendanceStage = datastoreAttendance.value?.programStage.orEmpty(),
                    attendanceDataElement = datastoreAttendance.value?.status.orEmpty(),
                    reasonDataElement = datastoreAttendance.value?.absenceReason.orEmpty(),
                    date = date,
                    dataElementIds = listOfNotNull(
                        schoolCalendar.value?.academicYear,
                        registration?.grade,
                        registration?.section,
                    ),
                    options = options.value,
                )
            }
            val data = response.await()

            setTeis(data.keys.toList())
            _attendanceStatus.value = data.values.toList()

            clearCache()
            attendanceCache.addAll(attendanceStatus.value)

            setInitialAttendanceStatus()

            _isLoading.value = false
        }
    }

    private fun setInitialAttendanceStatus() {
        attendanceBtnStateCache = attendanceStatus.value.map { attendance ->
            attendanceActionButtonMapper(
                index = attendanceOptions.value.indexOfFirst { it.code == attendance.value },
                tei = attendance.tei,
                attendanceValue = attendance.value,
                containerColor = attendanceOptions.value.find {
                    it.code == attendance.value
                }?.color ?: Color.Black,
            )
        }.toMutableList()

        _attendanceBtnState.value = attendanceBtnStateCache
        translateAttendanceStatus2FormData()
    }

    private fun translateAttendanceStatus2FormData() {
        _formData.value = attendanceStatus.value
            .filter { it.reasonOfAbsence != null }
            .map { status ->
                val formField = formFields.value.first()
                val option = formField
                    .getOption(status.reasonOfAbsence.orEmpty())

                FormData(
                    tei = status.tei,
                    event = status.event.orEmpty(),
                    date = null,
                    dataElement = formField.uid,
                    value = status.value,
                    valueType = null,
                    hasOptions = true,
                    itemOptions = option,
                )
            }
    }

    private fun attendanceActionButtonMapper(
        index: Int,
        tei: String,
        attendanceValue: String,
        containerColor: Color,
    ) = AttendanceActionButtonState(
        btnIndex = index,
        btnId = tei,
        iconTint = 0,
        buttonState = AttendanceButtonSettings(
            buttonType = attendanceValue,
            containerColor = containerColor,
            contentColor = WHITE,
        ),
    )

    private fun getAttendanceUiState(
        index: Int,
        tei: String,
        value: String,
        color: Color?,
    ): MutableList<AttendanceActionButtonState> {
        val uiCache = attendanceBtnStateCache.find { it.btnId == tei }

        val uiCacheItem = attendanceActionButtonMapper(
            index = index,
            tei = tei,
            attendanceValue = value,
            containerColor = color ?: Color.LightGray,
        )

        if (uiCache == null) {
            attendanceBtnStateCache.add(uiCacheItem)
        } else {
            attendanceBtnStateCache.remove(uiCache)
            attendanceBtnStateCache.add(uiCacheItem)
        }

        return attendanceBtnStateCache.toMutableList()
    }

    fun bulkAttendance(
        index: Int,
        value: String,
        reasonOfAbsence: String? = null,
        color: Color? = null,
    ) {
        val updatedBtnStates = mutableListOf<AttendanceActionButtonState>()

        teiUIds.value.forEach { (tei, enrollment) ->
            // Update formData if necessary (remove existing for this TEI)
            val data = formData.value.toMutableList()
            val formDataItem = data.find { it.tei == tei }
            if (formDataItem != null) {
                data.remove(formDataItem)
                _formData.value = data
            }

            // Create attendance entity
            val attendance = AttendanceEntity(
                tei = tei,
                enrollment = enrollment,
                dataElement = datastoreAttendance.value?.status.orEmpty(),
                value = value,
                reasonDataElement = datastoreAttendance.value?.absenceReason,
                reasonOfAbsence = reasonOfAbsence,
                date = eventDate.value,
            )

            // Update cache
            val cacheItem = attendanceCache.find { it.tei == attendance.tei }
            if (cacheItem == null) {
                attendanceCache.add(attendance)
            } else {
                attendanceCache.remove(cacheItem)
                attendanceCache.add(attendance)
            }

            // Create UI button state
            val uiCacheItem = attendanceActionButtonMapper(
                index = index,
                tei = tei,
                attendanceValue = value,
                containerColor = color ?: Color.LightGray,
            )
            updatedBtnStates.add(uiCacheItem)

            // Update formData for absences
            if (reasonOfAbsence != null) {
                val formField = formFields.value.firstOrNull()
                if (formField != null) {
                    val option = formField.getOption(reasonOfAbsence)
                    val newFormData = FormData(
                        tei = tei,
                        event = "",
                        date = null,
                        dataElement = formField.uid,
                        value = value,
                        valueType = null,
                        hasOptions = true,
                        itemOptions = option,
                    )
                    val existingFormData = formData.value.toMutableList()
                    existingFormData.add(newFormData)
                    _formData.value = existingFormData
                }
            }
        }

        // Update UI state with new list to trigger StateFlow emission immediately
        attendanceBtnStateCache = updatedBtnStates
        _attendanceBtnState.value = updatedBtnStates
    }

    fun setAttendance(
        index: Int,
        ou: String,
        tei: String,
        enrollment: String,
        value: String,
        reasonOfAbsence: String? = null,
        color: Color? = null,
        hasPersisted: Boolean = true,
    ) {
        viewModelScope.launch {
            val data = formData.value.toMutableList()

            val formDataItem = data.find { it.tei == tei }
            if (formDataItem != null) {
                data.remove(formDataItem)
                repository.deleteEvent(tei, enrollment, eventDate.value)
            }

            _attendanceBtnState.value = getAttendanceUiState(index, tei, value, color)

            val attendance = AttendanceEntity(
                tei = tei,
                enrollment = enrollment,
                dataElement = datastoreAttendance.value?.status.orEmpty(),
                value = value,
                reasonDataElement = datastoreAttendance.value?.absenceReason,
                reasonOfAbsence = reasonOfAbsence,
                date = eventDate.value,
            )

            val cacheItem = attendanceCache.find { it.tei == attendance.tei }

            if (cacheItem == null) {
                attendanceCache.add(attendance)
            } else {
                attendanceCache.remove(cacheItem)
                attendanceCache.add(attendance)
            }

            if (hasPersisted) {
                viewModelScope.launch {
                    repository.save(
                        ou = ou,
                        program = program.value,
                        programStage = datastoreAttendance.value?.programStage.orEmpty(),
                        attendance = attendance,
                    )
                }
            }
        }
    }

    fun setAbsence(
        index: Int? = null,
        ou: String? = null,
        tei: String? = null,
        enrollment: String? = null,
        value: String? = null,
        color: Color? = null,
        reasonOfAbsence: String? = null,
    ) {
        if (index != null) {
            _absenceState.update {
                it.copy(index = index)
            }
        }
        if (ou != null) {
            _absenceState.update {
                it.copy(ou = ou)
            }
        }
        if (tei != null) {
            _absenceState.update {
                it.copy(tei = tei)
            }
        }
        if (enrollment != null) {
            _absenceState.update {
                it.copy(enrollment = enrollment)
            }
        }
        if (value != null) {
            _absenceState.update {
                it.copy(value = value)
            }
        }
        if (color != null) {
            _absenceState.update {
                it.copy(color = color)
            }
        }
        if (reasonOfAbsence != null) {
            _absenceState.update {
                it.copy(reasonOfAbsence = reasonOfAbsence)
            }
        }
    }

    fun fieldState(
        key: String,
        event: String,
        dataElement: String,
        value: String,
        valueType: ValueType?,
    ) {
        val currentFields = fieldState.value.toMutableList()
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

        _fieldState.value = currentFields
    }

    override fun save() {
        setAttendance(
            index = absenceState.value.index,
            ou = absenceState.value.ou,
            enrollment = absenceState.value.enrollment,
            tei = absenceState.value.tei,
            value = absenceState.value.value,
            color = absenceState.value.color,
            reasonOfAbsence = absenceState.value.reasonOfAbsence,
        )

        val cache = mutableListOf<Absence>()

        cache.addAll(absenceStateCache.value)
        cache.add(absenceState.value)

        _absenceStateCache.value = cache
    }

    fun bulkSave(
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            async {
                attendanceCache.forEach { attendance ->
                    repository.save(
                        ou = ou.value,
                        program = program.value,
                        programStage = datastoreAttendance.value?.programStage.orEmpty(),
                        attendance = attendance,
                    )
                }
            }.await()

            clearCache()
            setAttendanceStep(ButtonStep.EDITING)
            if (isOnlyAbsence.value) {
                geTeiByAttendanceStatus(eventDate.value)
            } else {
                attendanceEvents(eventDate.value)
            }
            onSuccess()
        }
    }

    fun getSummary(): List<Summary> {
        val summaries = attendanceOptions.value.map { Pair(it.code, Triple(it.iconName, it.icon, it.color)) }
            .map { status ->

                val count = attendanceCache.count { it.value.equals(status.first, true) }

                Summary(
                    count,
                    status.second.first,
                    status.second.second,
                    status.second.third,
                )
            }

        return summaries
    }

    fun clearCache() {
        attendanceCache.clear()
        attendanceBtnStateCache.clear()
        _absenceStateCache.value = emptyList()
    }

    fun refreshOnSave() {
        setAttendanceStep(ButtonStep.EDITING)
        if (isOnlyAbsence.value) {
            geTeiByAttendanceStatus(eventDate.value)
        } else {
            attendanceEvents(eventDate.value)
        }
    }
}

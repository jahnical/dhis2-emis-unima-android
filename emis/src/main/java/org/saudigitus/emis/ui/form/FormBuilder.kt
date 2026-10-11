package org.saudigitus.emis.ui.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dhis2.composetable.ui.Keyboard
import org.dhis2.composetable.ui.keyboardAsState
import org.hisp.dhis.android.core.common.ValueType
import org.hisp.dhis.mobile.ui.designsystem.component.InputShellState
import org.saudigitus.emis.R
import org.saudigitus.emis.utils.findByCode

@Composable
fun FormBuilder(
    modifier: Modifier = Modifier,
    colors: TextFieldColors = TextFieldDefaults.colors(
        focusedIndicatorColor = InputShellState.FOCUSED.color,
        unfocusedIndicatorColor = InputShellState.UNFOCUSED.color,
        disabledIndicatorColor = InputShellState.DISABLED.color,
    ),
    enabled: Boolean = true,
    label: String? = null,
    state: List<Field>,
    key: String,
    fields: List<FormField>,
    formData: List<FormData>? = emptyList(),
    onNext: (Triple<String, String?, ValueType?>) -> Unit,
    setFormState: (
        key: String,
        event: String,
        dataElement: String,
        value: String,
        valueType: ValueType?,
    ) -> Unit,
    readOnly: List<String> = emptyList(),
    renderTextFieldsInRow: Boolean = false,
) {
    val formState = remember { mutableStateMapOf<String, String>() }

    val focusManager = LocalFocusManager.current
    val keyboardState by keyboardAsState()

    if (keyboardState == Keyboard.Closed) {
        focusManager.clearFocus(true)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var i = 0
        while (i < fields.size) {
            val formField = fields[i]
            val data = formData?.find { it.tei == key && it.dataElement == formField.uid }

            val selectedState = state.find { it.key == key && it.dataElement == formField.uid }
            val selectedItem = formField.options?.findByCode(
                selectedState?.value.orEmpty()
            )

            if (formField.hasOptions() || data?.hasOptions == true) {
                DropdownField(
                    label = if (fields.size == 1) label.orEmpty() else formField.label,
                    placeholder = formField.placeholder,
                    data = formField.options ?: emptyList(),
                    selectedItem = selectedItem ?: data?.itemOptions,
                    enabled = enabled && !readOnly.contains(formField.uid),
                    colors =
                    TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = InputShellState.UNFOCUSED.color,
                        disabledIndicatorColor = InputShellState.DISABLED.color,
                    ),
                ) { item ->
                    setFormState.invoke(
                        key,
                        data?.event.orEmpty(),
                        formField.uid,
                        item.code.orEmpty(),
                        null,
                    )
                    onNext(Triple(formField.uid, item.code, null))
                }
            } else {
                // Attempt inline pair rendering when requested and possible
                if (renderTextFieldsInRow && i + 1 < fields.size) {
                    val nextField = fields[i + 1]
                    val dataNext = formData?.find { it.tei == key && it.dataElement == nextField.uid }

                    // Pairs on either a real dropdown or a readOnly companion field (e.g. grade).
                    val nextIsCompanion = nextField.hasOptions() || dataNext?.hasOptions == true ||
                        readOnly.contains(nextField.uid)

                    // text input (score) + grade/dropdown companion side-by-side
                    if (nextIsCompanion) {
                        PairedMixedFields(
                            key = key,
                            textField = formField,
                            dropdownField = nextField,
                            textData = data,
                            dropdownData = dataNext,
                            state = state,
                            selectedState = selectedState,
                            formState = formState,
                            enabled = enabled,
                            readOnly = readOnly,
                            colors = colors,
                            label = label,
                            setFormState = setFormState,
                            onNext = onNext,
                        )
                        i += 2
                        continue
                    }
                }

                // fallback: original single InputField rendering
                InputField(
                    value = state.find { it.key == key && it.dataElement == formField.uid }?.value
                        ?: data?.value ?: "",
                    onValueChange = {
                        setFormState.invoke(
                            key,
                            data?.event.orEmpty(),
                            formField.uid,
                            it,
                            formField.type,
                        )
                    },
                    placeholder = formField.placeholder,
                    label = if (fields.size == 1) label else formField.label,
                    inputType = formField.type,
                    isError = selectedState?.hasError == true,
                    errorMessage = selectedState?.errorMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("FIELD_${formField.uid}")
                        .onFocusChanged {
                            if (!it.isFocused && (formState.isNotEmpty() || state.isNotEmpty())) {
                                val fieldValue = state.find { field ->
                                    field.key == key && field.dataElement == formField.uid
                                }
                                onNext(Triple(formField.uid, fieldValue?.value, formField.type))
                            }
                        },
                    enabled = enabled && !readOnly.contains(formField.uid),
                    colors = colors,
                )
            }

            i++
        }
    }
}

/** Read-only companion (e.g. grade): a Text for real ellipsis, tap to reveal the full value. */
@Composable
private fun ReadOnlyCompanionField(
    value: String,
    label: String?,
    modifier: Modifier = Modifier,
) {
    var showFullValue by remember { mutableStateOf(false) }
    val displayText = value.ifEmpty { stringResource(R.string.enter_score_to_see_grade) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = value.isNotEmpty()) { showFullValue = true }
                .padding(top = 8.dp, bottom = 4.dp),
        ) {
            if (!label.isNullOrEmpty()) {
                Text(text = label, fontSize = 12.sp, color = InputShellState.UNFOCUSED.color)
            }
            Text(
                text = displayText,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Divider(thickness = 1.dp, color = InputShellState.DISABLED.color)
        }

        DropdownMenu(
            expanded = showFullValue,
            onDismissRequest = { showFullValue = false },
        ) {
            DropdownMenuItem(
                text = { Text(value) },
                onClick = { showFullValue = false },
            )
        }
    }
}

@Composable
private fun PairedMixedFields(
    key: String,
    textField: FormField,
    dropdownField: FormField,
    textData: FormData?,
    dropdownData: FormData?,
    state: List<Field>,
    selectedState: Field?,
    formState: SnapshotStateMap<String, String>,
    enabled: Boolean,
    readOnly: List<String>,
    colors: TextFieldColors,
    label: String?,
    setFormState: (key: String, event: String, dataElement: String, value: String, valueType: ValueType?) -> Unit,
    onNext: (Triple<String, String?, ValueType?>) -> Unit,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val isNarrow = screenWidth< 390.dp
    val dropdownSelectedState = state.find { it.key == key && it.dataElement == dropdownField.uid }
    val dropdownSelectedItem = dropdownField.options?.findByCode(dropdownSelectedState?.value.orEmpty())
    val dropdownColors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = InputShellState.UNFOCUSED.color,
        disabledIndicatorColor = InputShellState.DISABLED.color,
    )
    // A readOnly companion (e.g. grade) renders as plain text, never a dropdown.
    val dropdownIsEditable = !readOnly.contains(dropdownField.uid) &&
        (dropdownField.hasOptions() || dropdownData?.hasOptions == true)
    val dropdownDisplayText = (dropdownSelectedItem ?: dropdownData?.itemOptions)?.toString()
        ?: dropdownData?.value
        ?: ""

    if (isNarrow) {
        Column {
            InputField(
                value = state.find { it.key == key && it.dataElement == textField.uid }?.value
                    ?: textData?.value ?: "",
                onValueChange = {
                    setFormState(key, textData?.event.orEmpty(), textField.uid, it, textField.type)
                },
                placeholder = textField.placeholder,
                label = label ?: textField.label,
                inputType = textField.type,
                isError = selectedState?.hasError == true,
                errorMessage = selectedState?.errorMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (!it.isFocused && (formState.isNotEmpty() || state.isNotEmpty())) {
                            val fieldValue = state.find { f -> f.key == key && f.dataElement == textField.uid }
                            onNext(Triple(textField.uid, fieldValue?.value, textField.type))
                        }
                    },
                enabled = enabled && !readOnly.contains(textField.uid),
                colors = colors,
            )
            if (dropdownIsEditable) {
                DropdownField(
                    label = dropdownField.label,
                    placeholder = dropdownField.placeholder,
                    data = dropdownField.options ?: emptyList(),
                    selectedItem = dropdownSelectedItem ?: dropdownData?.itemOptions,
                    enabled = enabled && !readOnly.contains(dropdownField.uid),
                    colors = dropdownColors,
                ) { item ->
                    setFormState(key, dropdownData?.event.orEmpty(), dropdownField.uid, item.code.orEmpty(), null)
                    onNext(Triple(dropdownField.uid, item.code, null))
                }
            } else {
                ReadOnlyCompanionField(
                    value = dropdownDisplayText,
                    label = dropdownField.label,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InputField(
                value = state.find { it.key == key && it.dataElement == textField.uid }?.value
                    ?: textData?.value ?: "",
                onValueChange = {
                    setFormState(key, textData?.event.orEmpty(), textField.uid, it, textField.type)
                },
                placeholder = textField.placeholder,
                label = label ?: textField.label,
                inputType = textField.type,
                isError = selectedState?.hasError == true,
                errorMessage = selectedState?.errorMessage,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged {
                        if (!it.isFocused && (formState.isNotEmpty() || state.isNotEmpty())) {
                            val fieldValue = state.find { f -> f.key == key && f.dataElement == textField.uid }
                            onNext(Triple(textField.uid, fieldValue?.value, textField.type))
                        }
                    },
                enabled = enabled && !readOnly.contains(textField.uid),
                colors = colors,
            )
            Box(modifier = Modifier.weight(1f)) {
                if (dropdownIsEditable) {
                    DropdownField(
                        label = dropdownField.label,
                        placeholder = dropdownField.placeholder,
                        data = dropdownField.options ?: emptyList(),
                        selectedItem = dropdownSelectedItem ?: dropdownData?.itemOptions,
                        enabled = enabled && !readOnly.contains(dropdownField.uid),
                        colors = dropdownColors,
                    ) { item ->
                        setFormState(key, dropdownData?.event.orEmpty(), dropdownField.uid, item.code.orEmpty(), null)
                        onNext(Triple(dropdownField.uid, item.code, null))
                    }
                } else {
                    ReadOnlyCompanionField(
                        value = dropdownDisplayText,
                        label = dropdownField.label,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

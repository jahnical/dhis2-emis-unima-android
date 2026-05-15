package org.saudigitus.emis.ui.subjects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.dhis2.commons.resources.ColorUtils
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import org.hisp.dhis.mobile.ui.designsystem.component.ListCard
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardColumn
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardTitleModel
import org.saudigitus.emis.R
import org.saudigitus.emis.data.model.mapper.map
import org.saudigitus.emis.ui.components.DetailsWithOptions
import org.saudigitus.emis.ui.components.InfoCard
import org.saudigitus.emis.ui.components.Toolbar
import org.saudigitus.emis.ui.components.ToolbarActionState
import org.saudigitus.emis.ui.teis.mapper.TEICardMapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectScreen(
    state: SubjectUIState,
    onBack: () -> Unit,
    onFilterClick: (String) -> Unit,
    infoCard: InfoCard,
    onClick: (String, String) -> Unit,
    sync: () -> Unit,
    teiCardMapper: TEICardMapper,
    onTabSelected: (SubjectTab) -> Unit,
    onStudentClick: (tei: String, name: String) -> Unit,
) {
    var displayName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Toolbar(
                headers = state.toolbarHeaders,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2C98F0),
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                navigationAction = { onBack.invoke() },
                disableNavigation = false,
                actionState = ToolbarActionState(
                    syncVisibility = true,
                    filterVisibility = false,
                    showCalendar = false,
                ),
                filterAction = {},
                syncAction = sync,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color(0xFF2C98F0))
                .padding(paddingValues),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = Color.White,
                        shape = MaterialTheme.shapes.medium
                            .copy(
                                topStart = CornerSize(16.dp),
                                topEnd = CornerSize(16.dp),
                                bottomStart = CornerSize(0.dp),
                                bottomEnd = CornerSize(0.dp),
                            ),
                    ),
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Top),
                horizontalAlignment = Alignment.Start,
            ) {

                DetailsWithOptions(
                    modifier = Modifier.fillMaxWidth(),
                    infoCard = infoCard,
                    placeholder = stringResource(R.string.select_term),
                    leadingIcon = Icons.Default.Event,
                    data = state.filters,
                    defaultSelection = displayName.ifEmpty {
                        state.filters.getOrNull(0)?.itemName ?: ""
                    },
                    onItemClick = {
                        displayName = it.itemName
                        onFilterClick.invoke(it.id)
                    },
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SubjectTab.entries.forEachIndexed { index, tab ->
                        SegmentedButton(
                            selected = state.selectedTab == tab,
                            onClick = { onTabSelected(tab) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = SubjectTab.entries.size,
                            ),
                            label = {
                                Text(
                                    text = when (tab) {
                                        SubjectTab.SUBJECTS -> stringResource(R.string.subject)
                                        SubjectTab.STUDENTS -> stringResource(R.string.students)
                                    },
                                )
                            },
                        )
                    }
                }

                when (state.selectedTab) {
                    SubjectTab.SUBJECTS -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 12.dp),
                        ) {
                            items(state.subjects) { subject ->
                                SubjectItem(
                                    displayName = subject.displayName ?: "-",
                                    attrValue = displayName,
                                    color = if (subject.color != null) {
                                        Color(ColorUtils().parseColor(subject.color))
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        onClick.invoke(subject.uid, subject.displayName ?: "-")
                                    },
                                )
                            }
                        }
                    }

                    SubjectTab.STUDENTS -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            items(state.students) { student ->
                                val cardModel = student.map(teiCardMapper, showSync = false)
                                val isInactive =
                                    student.enrollments.getOrNull(0)?.status() ==
                                        EnrollmentStatus.CANCELLED
                                val card = cardModel.copy(
                                    onCardCLick = {
                                        onStudentClick(student.uid(), cardModel.title)
                                    },
                                )
                                ListCardColumn(
                                    modifier = Modifier.background(
                                        color = if (isInactive) {
                                            Color.LightGray.copy(.65f)
                                        } else {
                                            Color.White
                                        },
                                    ),
                                ) {
                                    ListCard(
                                        modifier = Modifier.background(
                                            color = if (isInactive) {
                                                Color.LightGray.copy(.25f)
                                            } else {
                                                Color.White
                                            },
                                        ),
                                        listAvatar = card.avatar,
                                        title = ListCardTitleModel(text = card.title),
                                        additionalInfoList = card.additionalInfo,
                                        actionButton = {},
                                        expandLabelText = card.expandLabelText,
                                        shrinkLabelText = card.shrinkLabelText,
                                        onCardClick = card.onCardCLick,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
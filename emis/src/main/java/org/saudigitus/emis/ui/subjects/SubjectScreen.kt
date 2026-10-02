package org.saudigitus.emis.ui.subjects

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dhis2.commons.resources.ColorUtils
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import org.hisp.dhis.mobile.ui.designsystem.component.AdditionalInfoItem
import org.hisp.dhis.mobile.ui.designsystem.component.ListCard
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardColumn
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardTitleModel
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberListCardState
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberAdditionalInfoColumnState
import org.saudigitus.emis.R
import org.saudigitus.emis.data.model.mapper.map
import org.saudigitus.emis.ui.components.DetailsWithOptions
import org.saudigitus.emis.ui.components.EdgeOverscrollShadow
import org.saudigitus.emis.ui.components.ExpandableSearchRow
import org.saudigitus.emis.ui.components.InfoCard
import org.saudigitus.emis.ui.components.OverscrollBounceSpring
import org.saudigitus.emis.ui.components.OverscrollMaxDrag
import org.saudigitus.emis.ui.components.Toolbar
import org.saudigitus.emis.ui.components.ToolbarActionState
import org.saudigitus.emis.ui.components.rubberBandOffset
import org.saudigitus.emis.ui.teis.mapper.TEICardMapper
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectScreen(
    state: SubjectUIState,
    selectedStage: String,
    onBack: () -> Unit,
    onFilterClick: (String) -> Unit,
    infoCard: InfoCard,
    onClick: (String, String) -> Unit,
    sync: () -> Unit,
    teiCardMapper: TEICardMapper,
    onTabSelected: (SubjectTab) -> Unit,
    onStudentClick: (tei: String, name: String) -> Unit,
) {
    // Derived from the ViewModel's selection so it survives navigating away and back.
    val selectedTermName = state.filters.find { it.id == selectedStage }?.itemName
        ?: state.filters.getOrNull(0)?.itemName
        ?: ""
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            delay(300)
            debouncedQuery = searchQuery
        } else {
            debouncedQuery = ""
        }
    }

    val filteredSubjects = remember(state.subjects, debouncedQuery) {
        if (debouncedQuery.isEmpty()) state.subjects
        else state.subjects.filter {
            it.displayName?.contains(debouncedQuery, ignoreCase = true) == true
        }
    }

    val studentEntries = remember(state.students) {
        state.students.map { student ->
            student to student.map(teiCardMapper, showSync = false)
        }
    }

    val filteredStudentEntries = remember(studentEntries, debouncedQuery) {
        if (debouncedQuery.isEmpty()) studentEntries
        else studentEntries.filter { (_, card) ->
            card.title.contains(debouncedQuery, ignoreCase = true)
        }
    }

    // Read via rememberUpdatedState so the swipe gesture below always sees the
    // latest tab without needing to restart mid-drag.
    val latestSelectedTab by rememberUpdatedState(state.selectedTab)

    fun selectTabByOffset(offset: Int) {
        val entries = SubjectTab.entries
        val currentIndex = entries.indexOf(latestSelectedTab)
        val target = entries.getOrNull(currentIndex + offset) ?: return
        onTabSelected(target)
    }

    val density = LocalDensity.current
    val overscrollScope = rememberCoroutineScope()
    val overscrollOffset = remember { Animatable(0f) }
    val maxOverscrollPx = with(density) { OverscrollMaxDrag.toPx() }

    val collapsedTabIcon = when (state.selectedTab) {
        SubjectTab.SUBJECTS -> painterResource(R.drawable.subject_icon)
        SubjectTab.STUDENTS -> rememberVectorPainter(Icons.Outlined.Person)
    }

    val searchPlaceholder = when (state.selectedTab) {
        SubjectTab.SUBJECTS -> stringResource(R.string.search_subjects)
        SubjectTab.STUDENTS -> stringResource(R.string.search_students)
    }

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
                    )
                    // Swipe to toggle between the Subjects and Students tabs, same as
                    // tapping the segmented buttons below. Dragging past either end
                    // (already on the first/last tab) resists instead, with a bounce
                    // back on release, to signal there's nothing more that way.
                    .pointerInput(Unit) {
                        val swipeThresholdPx = with(density) { 96.dp.toPx() }
                        var accumulatedDrag = 0f

                        fun settleOverscroll() {
                            overscrollScope.launch {
                                overscrollOffset.animateTo(0f, OverscrollBounceSpring)
                            }
                        }

                        detectHorizontalDragGestures(
                            onDragStart = { accumulatedDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                accumulatedDrag += dragAmount
                                change.consume()

                                val liveIndex = SubjectTab.entries.indexOf(latestSelectedTab)
                                val atBoundary = (accumulatedDrag > 0 && liveIndex <= 0) ||
                                    (accumulatedDrag < 0 && liveIndex >= SubjectTab.entries.lastIndex)

                                overscrollScope.launch {
                                    if (atBoundary) {
                                        overscrollOffset.snapTo(
                                            rubberBandOffset(accumulatedDrag, maxOverscrollPx),
                                        )
                                    } else if (overscrollOffset.value != 0f) {
                                        overscrollOffset.snapTo(0f)
                                    }
                                }
                            },
                            onDragEnd = {
                                when {
                                    accumulatedDrag <= -swipeThresholdPx -> selectTabByOffset(1)
                                    accumulatedDrag >= swipeThresholdPx -> selectTabByOffset(-1)
                                }
                                settleOverscroll()
                            },
                            onDragCancel = { settleOverscroll() },
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Top),
                horizontalAlignment = Alignment.Start,
            ) {
                DetailsWithOptions(
                    modifier = Modifier.fillMaxWidth(),
                    infoCard = infoCard,
                    placeholder = stringResource(R.string.select_term),
                    leadingIcon = Icons.Default.Event,
                    data = state.filters,
                    defaultSelection = selectedTermName,
                    onItemClick = {
                        onFilterClick.invoke(it.id)
                    },
                )

                ExpandableSearchRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    searchPlaceholder = searchPlaceholder,
                    collapsedPrimaryIcon = collapsedTabIcon,
                    collapsedPrimaryContentDescription = state.selectedTab.name,
                    onSearchActiveChange = { active: Boolean ->
                        isSearchActive = active
                        if (!active) searchQuery = ""
                    },
                    onSearchQueryChange = { searchQuery = it },
                    primaryContent = {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
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
                    },
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = state.selectedTab,
                        transitionSpec = {
                            if (targetState.ordinal >= initialState.ordinal) {
                                (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                                    (slideOutHorizontally { width -> -width } + fadeOut())
                            } else {
                                (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                                    (slideOutHorizontally { width -> width } + fadeOut())
                            }
                        },
                        label = "subjectTabContent",
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(overscrollOffset.value.roundToInt(), 0) },
                    ) { tab ->
                        when (tab) {
                            SubjectTab.SUBJECTS -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 12.dp),
                                ) {
                                    items(filteredSubjects) { subject ->
                                        SubjectItem(
                                            displayName = subject.displayName ?: "-",
                                            attrValue = selectedTermName,
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
                                    items(filteredStudentEntries) { (student, card) ->
                                        val isInactive =
                                            student.enrollments.getOrNull(0)?.status() ==
                                                EnrollmentStatus.CANCELLED
                                        val cardWithClick = card.copy(
                                            onCardCLick = {
                                                onStudentClick(student.uid(), card.title)
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
                                                listAvatar = cardWithClick.avatar,
                                                listCardState = rememberListCardState(
                                                        title = ListCardTitleModel(text = cardWithClick.title),
                                                        additionalInfoColumnState = rememberAdditionalInfoColumnState(
                                                            additionalInfoList = cardWithClick.additionalInfo,
                                                            syncProgressItem = AdditionalInfoItem(key = "", value = ""),
                                                            expandLabelText = cardWithClick.expandLabelText,
                                                            shrinkLabelText = cardWithClick.shrinkLabelText,
                                                        ),
                                                    ),
                                                actionButton = {},
                                                onCardClick = cardWithClick.onCardCLick,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    EdgeOverscrollShadow(offsetPx = overscrollOffset.value, maxPx = maxOverscrollPx)
                }
            }
        }
    }
}

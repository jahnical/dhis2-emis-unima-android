package org.saudigitus.emis.ui.studentsummary

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.saudigitus.emis.R
import org.saudigitus.emis.data.model.SubjectResult
import org.saudigitus.emis.data.model.mapper.map
import org.saudigitus.emis.ui.components.DetailsWithOptions
import org.saudigitus.emis.ui.components.DropdownItem
import org.saudigitus.emis.ui.components.EdgeOverscrollShadow
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
fun StudentSummaryScreen(
    state: StudentSummaryUiState,
    infoCard: InfoCard,
    teiCardMapper: TEICardMapper,
    onBack: () -> Unit,
    onStudentSelected: (tei: String, name: String) -> Unit,
) {
    var selectedStudentName by remember { mutableStateOf("") }

    val studentItems = remember(state.students) {
        state.students.map { student ->
            val cardModel = student.map(teiCardMapper, showSync = false)
            DropdownItem(id = student.uid(), itemName = cardModel.title)
        }
    }

    fun selectStudent(item: DropdownItem) {
        selectedStudentName = item.itemName
        onStudentSelected(item.id, item.itemName)
    }

    // Read via rememberUpdatedState so the swipe gesture below always sees the
    // latest list/selection without needing to restart mid-drag.
    val latestStudentItems by rememberUpdatedState(studentItems)
    val latestSelectedTei by rememberUpdatedState(state.selectedTei)

    fun selectByOffset(offset: Int) {
        val currentIndex = latestStudentItems.indexOfFirst { it.id == latestSelectedTei }
        if (currentIndex == -1) return
        val target = latestStudentItems.getOrNull(currentIndex + offset) ?: return
        selectStudent(target)
    }

    // Drives the slide direction: the content animates forward/backward
    // depending on whether the newly selected student moved up or down the list.
    val currentIndex = studentItems.indexOfFirst { it.id == state.selectedTei }

    val density = LocalDensity.current
    val overscrollScope = rememberCoroutineScope()
    val overscrollOffset = remember { Animatable(0f) }
    val maxOverscrollPx = with(density) { OverscrollMaxDrag.toPx() }

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
                navigationAction = onBack,
                disableNavigation = false,
                actionState = ToolbarActionState(
                    syncVisibility = false,
                    filterVisibility = false,
                    showCalendar = false,
                ),
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
                        shape = MaterialTheme.shapes.medium.copy(
                            topStart = CornerSize(16.dp),
                            topEnd = CornerSize(16.dp),
                            bottomStart = CornerSize(0.dp),
                            bottomEnd = CornerSize(0.dp),
                        ),
                    )
                    // Swipe to move to the previous/next student, same as picking
                    // them from the dropdown above. Dragging past the first/last
                    // student resists instead, with a bounce back on release, to
                    // signal there's nothing more in that direction.
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

                                val liveIndex = latestStudentItems
                                    .indexOfFirst { it.id == latestSelectedTei }
                                val atBoundary = (accumulatedDrag > 0 && liveIndex <= 0) ||
                                    (accumulatedDrag < 0 && liveIndex >= latestStudentItems.lastIndex)

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
                                    accumulatedDrag <= -swipeThresholdPx -> selectByOffset(1)
                                    accumulatedDrag >= swipeThresholdPx -> selectByOffset(-1)
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
                    placeholder = stringResource(R.string.students),
                    leadingIcon = Icons.Default.Person,
                    data = studentItems,
                    defaultSelection = selectedStudentName.ifEmpty { state.toolbarHeaders.title },
                    onItemClick = { item -> selectStudent(item) },
                )

//

                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = currentIndex,
                        transitionSpec = {
                            if (targetState >= initialState) {
                                (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                                    (slideOutHorizontally { width -> -width } + fadeOut())
                            } else {
                                (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                                    (slideOutHorizontally { width -> width } + fadeOut())
                            }
                        },
                        label = "studentSummaryContent",
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(overscrollOffset.value.roundToInt(), 0) },
                    ) { _ ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            item {
                                ResultHeaderRow()
                            }
                            items(state.results) { result ->
                                SubjectResultRow(result = result)
                            }
                            item {
                                TotalScoreRow(
                                    totalScore = state.totalScore,
                                    termRemark = state.termRemark
                                )
                            }
                        }
                    }
                    EdgeOverscrollShadow(offsetPx = overscrollOffset.value, maxPx = maxOverscrollPx)
                }
            }
        }
    }
}

@Composable
private fun TermRemarkBanner(termRemark: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE3F2FD))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.term_remark),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black.copy(.7f),
        )
        Text(
            text = termRemark,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2C98F0),
        )
    }
}

@Composable
private fun TotalScoreRow(totalScore: String?, termRemark: String?) {
    Divider(thickness = 1.dp, color = Color.LightGray.copy(.5f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray.copy(.1f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.total_score),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.Black.copy(.85f),
        )
        Text(
            modifier = Modifier.width(72.dp),
            text = totalScore ?: "-",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color(0xFF2C98F0),
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.width(80.dp),
            text = termRemark ?: "-",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C98F0),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray.copy(.15f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.subject),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = Color.Black.copy(.6f),
        )
        Text(
            modifier = Modifier.width(72.dp),
            text = stringResource(R.string.score),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = Color.Black.copy(.6f),
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.width(80.dp),
            text = stringResource(R.string.gradeSection),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = Color.Black.copy(.6f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SubjectResultRow(result: SubjectResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = result.subjectName,
            fontSize = 14.sp,
            color = Color.Black.copy(.85f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            modifier = Modifier.width(72.dp),
            text = result.score ?: "—",
            fontSize = 14.sp,
            color = Color.Black.copy(.7f),
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.width(80.dp),
            text = result.gradeDisplayName ?: "—",
            fontSize = 14.sp,
            color = Color.Black.copy(.7f),
            textAlign = TextAlign.Center,
        )
    }
    Divider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = .5.dp,
        color = Color.LightGray.copy(.5f),
    )
}

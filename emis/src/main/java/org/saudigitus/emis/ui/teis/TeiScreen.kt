package org.saudigitus.emis.ui.teis

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus
import org.hisp.dhis.android.core.icon.Icon
import org.hisp.dhis.mobile.ui.designsystem.component.AdditionalInfoItem
import org.hisp.dhis.mobile.ui.designsystem.component.AdditionalInfoItemColor
import org.hisp.dhis.mobile.ui.designsystem.component.ListCard
import org.hisp.dhis.mobile.ui.designsystem.component.ListCardTitleModel
import org.hisp.dhis.mobile.ui.designsystem.component.SelectionState
import org.saudigitus.emis.R
import org.saudigitus.emis.data.model.mapper.map
import org.saudigitus.emis.ui.components.NoResults
import org.saudigitus.emis.ui.components.ShowCard
import org.saudigitus.emis.ui.components.Toolbar
import org.saudigitus.emis.ui.components.ToolbarActionState
import org.saudigitus.emis.ui.home.HomeViewModel
import org.saudigitus.emis.ui.teis.mapper.TEICardMapper
import org.hisp.dhis.mobile.ui.designsystem.component.state.ListCardState
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberAdditionalInfoColumnState
import org.hisp.dhis.mobile.ui.designsystem.component.state.rememberListCardState

@SuppressLint("CoroutineCreationDuringComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeiScreen(
    viewModel: HomeViewModel,
    teiCardMapper: TEICardMapper,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onCardClick: (tei: String, enrollment: String) -> Unit,
) {
    val students by viewModel.teis.collectAsStateWithLifecycle()
    val toolbarHeaders by viewModel.toolbarHeaders.collectAsStateWithLifecycle()
    val infoCard by viewModel.infoCard.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Toolbar(
                headers = toolbarHeaders,
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
                    showFavorite = false,
                ),
                syncAction = onSync,
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
                if (students.isEmpty()) {
                    NoResults(message = stringResource(R.string.search_no_results))
                } else {
                    ShowCard(infoCard)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(students) { student ->
                            val card = student.map(teiCardMapper, showSync = false, onCardClick = onCardClick)
                            val isInactive = student.enrollments.getOrNull(0)?.status() == EnrollmentStatus.CANCELLED

                            /*ListCard(
                                modifier = Modifier.testTag("TEI_ITEM")
                                    .background(
                                        color = if (isInactive) Color.LightGray.copy(.25f) else Color.White,
                                    ),
                                listAvatar = card.avatar,
                                title = ListCardTitleModel(text = card.title),
                                lastUpdated = card.lastUpdated,
                                additionalInfoList = card.additionalInfo,
                                actionButton = card.actionButton,
                                expandLabelText = card.expandLabelText,
                                shrinkLabelText = card.shrinkLabelText,
                                onCardClick = card.onCardCLick,
                            )*/

                            val additionalInfoItem = AdditionalInfoItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Android,
                                        contentDescription = "Status",
                                        tint = AdditionalInfoItemColor.SUCCESS.color,
                                    )
                                },
                                key = "Status",
                                value = "Active",
                                isConstantItem = true,
                                color = AdditionalInfoItemColor.SUCCESS.color,
                            )

                            val additionalInfoColumnState = rememberAdditionalInfoColumnState(
                                additionalInfoList = card.additionalInfo,
                                syncProgressItem = additionalInfoItem, // provide your sync item here
                                expandLabelText = card.expandLabelText,
                                shrinkLabelText = card.shrinkLabelText,
                            )

                            val cardState = rememberListCardState(
                                title = ListCardTitleModel(text = card.title),
                                lastUpdated = card.lastUpdated,
                                additionalInfoColumnState = additionalInfoColumnState,
                            )

                            ListCard(
                                modifier = Modifier
                                    .testTag("TEI_ITEM")
                                    .background(
                                        color = if (isInactive) Color.LightGray.copy(.25f) else Color.White,
                                    ),
                                listCardState = cardState,
                                listAvatar = card.avatar,
                                actionButton = card.actionButton,
                                onCardClick = card.onCardCLick,
                            )
                        }
                    }
                }
            }
        }
    }
}

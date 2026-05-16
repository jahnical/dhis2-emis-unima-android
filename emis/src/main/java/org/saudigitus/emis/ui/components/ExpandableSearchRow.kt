package org.saudigitus.emis.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun ExpandableSearchRow(
    modifier: Modifier = Modifier,
    isSearchActive: Boolean,
    searchQuery: String,
    searchPlaceholder: String,
    collapsedPrimaryIcon: Painter,
    collapsedPrimaryContentDescription: String,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    primaryContent: @Composable () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    val primaryWeight by animateFloatAsState(
        targetValue = if (isSearchActive) 0.25f else 0.75f,
        animationSpec = tween(durationMillis = 300),
        label = "primaryWeight",
    )
    val secondaryWeight = 1f - primaryWeight

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Primary slot — toggler
        Box(
            modifier = Modifier.weight(primaryWeight),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "primaryContent",
            ) { searchActive ->
                if (searchActive) {
                    IconButton(onClick = {
                        onSearchQueryChange("")
                        onSearchActiveChange(false)
                        keyboard?.hide()
                    }) {
                        Icon(
                            painter = collapsedPrimaryIcon,
                            contentDescription = collapsedPrimaryContentDescription,
                        )
                    }
                } else {
                    primaryContent()
                }
            }
        }

        // Secondary slot — search
        Box(
            modifier = Modifier.weight(secondaryWeight),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "searchContent",
            ) { searchActive ->
                if (searchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text(searchPlaceholder) },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = "")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                    )
                } else {
                    IconButton(onClick = { onSearchActiveChange(true) }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            }
        }
    }
}

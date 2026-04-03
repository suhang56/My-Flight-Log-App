@file:OptIn(ExperimentalFoundationApi::class)

package com.flightlog.app.ui.calendarflights

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.flightlog.app.data.local.entity.CalendarFlight
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.ui.logbook.AircraftPhotoState
import androidx.compose.runtime.SideEffect
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FlightBottomDrawer(
    filteredFlights: List<CalendarFlight>,
    upcomingFlights: List<CalendarFlight>,
    pastFlights: List<CalendarFlight>,
    selectedFlight: CalendarFlight?,
    drawerAnchor: DrawerAnchor,
    selectedTab: FlightTab,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onTabChanged: (FlightTab) -> Unit,
    onFlightCardTapped: (CalendarFlight) -> Unit,
    onDrawerAnchorChanged: (DrawerAnchor) -> Unit,
    onDismissFlight: (CalendarFlight) -> Unit,
    onAddToLogbook: suspend (CalendarFlight) -> Boolean,
    isAlreadyLogged: suspend (Long) -> Boolean,
    onLogbookSuccess: () -> Unit,
    onSyncClick: () -> Unit,
    getLinkedLogbookFlight: suspend (Long) -> LogbookFlight?,
    onRatingChanged: (Long, Int?) -> Unit,
    aircraftPhotoState: AircraftPhotoState = AircraftPhotoState(),
    onAircraftTypeResolved: (aircraftType: String?, registration: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeightDp.toPx() }

    val collapsedOffsetPx = screenHeightPx - with(density) { 240.dp.toPx() }
    val halfExpandedOffsetPx = screenHeightPx - with(density) { 360.dp.toPx() }
    val fullExpandedOffsetPx = screenHeightPx * 0.1f

    val anchoredDraggableState = remember {
        AnchoredDraggableState(
            initialValue = DrawerAnchor.COLLAPSED,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { 500f },
            snapAnimationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMediumLow
            ),
            decayAnimationSpec = exponentialDecay()
        )
    }

    SideEffect {
        anchoredDraggableState.updateAnchors(
            DraggableAnchors {
                DrawerAnchor.COLLAPSED at collapsedOffsetPx
                DrawerAnchor.HALF_EXPANDED at halfExpandedOffsetPx
                DrawerAnchor.FULL_EXPANDED at fullExpandedOffsetPx
            }
        )
    }

    // Sync: AnchoredDraggableState -> ViewModel (user dragged)
    var isProgrammaticChange by remember { mutableStateOf(false) }

    LaunchedEffect(anchoredDraggableState.currentValue) {
        if (!isProgrammaticChange) {
            onDrawerAnchorChanged(anchoredDraggableState.currentValue)
        }
    }

    // Sync: ViewModel -> AnchoredDraggableState (programmatic, e.g. flight card tap)
    LaunchedEffect(drawerAnchor) {
        if (anchoredDraggableState.currentValue != drawerAnchor) {
            isProgrammaticChange = true
            try {
                anchoredDraggableState.animateTo(drawerAnchor)
            } finally {
                isProgrammaticChange = false
            }
        }
    }

    val currentAnchor = anchoredDraggableState.currentValue

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .offset {
                IntOffset(
                    0,
                    anchoredDraggableState
                        .requireOffset()
                        .roundToInt()
                )
            }
            .anchoredDraggable(
                state = anchoredDraggableState,
                orientation = Orientation.Vertical,
            ),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp)
                    .semantics { contentDescription = "Drag to expand or collapse flight details" },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            when {
                currentAnchor == DrawerAnchor.COLLAPSED || selectedFlight == null -> {
                    CollapsedContent(
                        filteredFlights = filteredFlights,
                        upcomingCount = upcomingFlights.size,
                        pastCount = pastFlights.size,
                        selectedTab = selectedTab,
                        searchQuery = searchQuery,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onTabChanged = onTabChanged,
                        onFlightCardTapped = onFlightCardTapped,
                        onSyncClick = onSyncClick
                    )
                }
                currentAnchor == DrawerAnchor.HALF_EXPANDED -> {
                    FlightBriefCard(
                        flight = selectedFlight,
                        onDismiss = onDismissFlight,
                        onAddToLogbook = onAddToLogbook,
                        isAlreadyLogged = isAlreadyLogged,
                        onLogbookSuccess = onLogbookSuccess
                    )
                }
                currentAnchor == DrawerAnchor.FULL_EXPANDED -> {
                    FlightDetailContent(
                        flight = selectedFlight,
                        onDismissFlight = onDismissFlight,
                        onAddToLogbook = onAddToLogbook,
                        isAlreadyLogged = isAlreadyLogged,
                        onLogbookSuccess = onLogbookSuccess,
                        getLinkedLogbookFlight = getLinkedLogbookFlight,
                        onRatingChanged = onRatingChanged,
                        aircraftPhotoState = aircraftPhotoState,
                        onAircraftTypeResolved = onAircraftTypeResolved
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedContent(
    filteredFlights: List<CalendarFlight>,
    upcomingCount: Int,
    pastCount: Int,
    selectedTab: FlightTab,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onTabChanged: (FlightTab) -> Unit,
    onFlightCardTapped: (CalendarFlight) -> Unit,
    onSyncClick: () -> Unit
) {
    // Search bar
    DrawerSearchBar(
        query = searchQuery,
        onQueryChanged = onSearchQueryChanged,
        modifier = Modifier.padding(horizontal = 16.dp)
    )

    Spacer(modifier = Modifier.height(12.dp))

    if (filteredFlights.isEmpty() && searchQuery.isBlank()) {
        // Empty state
        DrawerEmptyState(onSyncClick = onSyncClick)
    } else {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeaderTab(
                text = "Past ($pastCount)",
                isActive = selectedTab == FlightTab.PAST,
                onClick = { onTabChanged(FlightTab.PAST) }
            )
            SectionHeaderTab(
                text = "Upcoming ($upcomingCount)",
                isActive = selectedTab == FlightTab.UPCOMING,
                onClick = { onTabChanged(FlightTab.UPCOMING) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredFlights.isEmpty()) {
            // Search yielded no results
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No flights match \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = filteredFlights,
                    key = { it.id }
                ) { flight ->
                    FlightCard(
                        flight = flight,
                        onClick = { onFlightCardTapped(flight) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderTab(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val color = if (isActive) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val weight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold
    else androidx.compose.ui.text.font.FontWeight.Normal

    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = weight,
            color = color
        )
    }
}

@Composable
private fun DrawerSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search flights",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search flights...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChanged("") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerEmptyState(onSyncClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.FlightTakeoff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No flights yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add calendar events like \"Flight AA0011 ORD-CMH\" and sync to see them here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onSyncClick) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sync Now")
        }
    }
}

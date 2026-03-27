package com.flightlog.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.flightlog.app.MainActivity
import com.flightlog.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class FlightLogWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            androidx.compose.ui.unit.DpSize(110.dp, 110.dp),
            androidx.compose.ui.unit.DpSize(250.dp, 110.dp)
        )
    )

    override val stateDefinition = WidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }
}

@Composable
private fun WidgetContent() {
    val prefs = currentState<Preferences>()
    val size = LocalSize.current

    val flightCount = prefs[WidgetDataKeys.FLIGHT_COUNT] ?: 0
    val distanceNm = prefs[WidgetDataKeys.TOTAL_DISTANCE_NM] ?: 0
    val lastDep = prefs[WidgetDataKeys.LAST_FLIGHT_DEP]
    val lastArr = prefs[WidgetDataKeys.LAST_FLIGHT_ARR]
    val lastDateMs = prefs[WidgetDataKeys.LAST_FLIGHT_DATE]

    val openAppAction = actionStartActivity<MainActivity>()

    if (size.width >= 250.dp) {
        MediumWidgetContent(flightCount, distanceNm, lastDep, lastArr, lastDateMs, openAppAction)
    } else {
        SmallWidgetContent(flightCount, distanceNm, openAppAction)
    }
}

@Composable
private fun SmallWidgetContent(
    flightCount: Int,
    distanceNm: Int,
    openAppAction: androidx.glance.action.Action
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(openAppAction)
            .padding(16.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "MY FLIGHT LOG",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = GlanceModifier.height(12.dp))
            if (flightCount == 0) {
                Text(
                    text = "No flights yet",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                )
            } else {
                Text(
                    text = "$flightCount ${if (flightCount == 1) "flight" else "flights"}",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "%,d nm".format(distanceNm),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun MediumWidgetContent(
    flightCount: Int,
    distanceNm: Int,
    lastDep: String?,
    lastArr: String?,
    lastDateMs: Long?,
    openAppAction: androidx.glance.action.Action
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(openAppAction)
            .padding(16.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MY FLIGHT LOG",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_flight_widget),
                    contentDescription = null,
                    modifier = GlanceModifier.size(24.dp)
                )
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            if (flightCount == 0) {
                Text(
                    text = "No flights yet \u2014 open app to add",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                )
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "$flightCount ${if (flightCount == 1) "flight" else "flights"}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(16.dp))
                    Text(
                        text = "%,d nm".format(distanceNm),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                if (lastDep != null || lastArr != null) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    val depDisplay = if (lastDep.isNullOrBlank()) "\u2014" else lastDep
                    val arrDisplay = if (lastArr.isNullOrBlank()) "\u2014" else lastArr
                    val dateDisplay = lastDateMs?.let { formatWidgetDate(it) } ?: ""
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last: $depDisplay\u2192$arrDisplay",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        )
                        if (dateDisplay.isNotBlank()) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Text(
                                text = dateDisplay,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatWidgetDate(epochMs: Long): String {
    return try {
        val instant = Instant.ofEpochMilli(epochMs)
        val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
        localDate.format(formatter)
    } catch (e: Exception) {
        ""
    }
}

class FlightLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = FlightLogWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefreshWorker.enqueueOnce(context)
    }
}

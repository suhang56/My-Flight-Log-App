package com.flightlog.app.ui.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.glance.state.GlanceStateDefinition
import java.io.File

object WidgetStateDefinition : GlanceStateDefinition<Preferences> {

    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<Preferences> {
        return context.widgetDataStore
    }

    override fun getLocation(context: Context, fileKey: String): File {
        return context.filesDir.resolve("datastore").resolve("flight_log_widget.preferences_pb")
    }
}

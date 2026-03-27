package com.flightlog.app.ui.widget

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object WidgetDataKeys {
    val FLIGHT_COUNT = intPreferencesKey("widget_flight_count")
    val TOTAL_DISTANCE_NM = intPreferencesKey("widget_total_distance_nm")
    val LAST_FLIGHT_DEP = stringPreferencesKey("widget_last_dep")
    val LAST_FLIGHT_ARR = stringPreferencesKey("widget_last_arr")
    val LAST_FLIGHT_DATE = longPreferencesKey("widget_last_flight_date_utc")
    val LAST_UPDATED_MS = longPreferencesKey("widget_last_updated_ms")
}

package com.flightlog.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.flightlog.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_STATUS = "flight_status"
        const val CHANNEL_DELAY = "flight_delay"

        fun createChannels(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val statusChannel = NotificationChannel(
                CHANNEL_STATUS,
                "Flight Status",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Departure, landing, and cancellation alerts"
            }

            val delayChannel = NotificationChannel(
                CHANNEL_DELAY,
                "Flight Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Gate changes and delay notifications"
            }

            manager.createNotificationChannels(listOf(statusChannel, delayChannel))
        }
    }

    fun notifyDeparted(flightNumber: String, departureCode: String) {
        send(
            channel = CHANNEL_STATUS,
            title = "$flightNumber Departed",
            text = "$flightNumber has departed $departureCode",
            notificationId = flightNumber.hashCode() + 1
        )
    }

    fun notifyLanded(flightNumber: String, arrivalCode: String) {
        send(
            channel = CHANNEL_STATUS,
            title = "$flightNumber Landed",
            text = "$flightNumber has landed at $arrivalCode",
            notificationId = flightNumber.hashCode() + 2
        )
    }

    fun notifyCancelled(flightNumber: String) {
        send(
            channel = CHANNEL_STATUS,
            title = "$flightNumber Cancelled",
            text = "$flightNumber has been cancelled",
            notificationId = flightNumber.hashCode() + 3
        )
    }

    fun notifyDelay(flightNumber: String, delayMinutes: Int, estimatedDepartureUtc: Long?) {
        val timeText = estimatedDepartureUtc?.let {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val localTime = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(formatter)
            " -- new departure $localTime"
        } ?: ""
        send(
            channel = CHANNEL_DELAY,
            title = "$flightNumber Delayed",
            text = "$flightNumber delayed ${delayMinutes} min$timeText",
            notificationId = flightNumber.hashCode() + 4
        )
    }

    fun notifyGateChange(flightNumber: String, newGate: String) {
        send(
            channel = CHANNEL_DELAY,
            title = "$flightNumber Gate Change",
            text = "$flightNumber gate changed to $newGate",
            notificationId = flightNumber.hashCode() + 5
        )
    }

    private fun send(channel: String, title: String, text: String, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}

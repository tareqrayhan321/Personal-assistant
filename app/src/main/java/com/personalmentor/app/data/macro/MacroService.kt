package com.personalmentor.app.data.macro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.personalmentor.app.domain.macro.Macro
import com.personalmentor.app.domain.macro.MacroEvent
import com.personalmentor.app.domain.macro.MacroExecutor
import com.personalmentor.app.domain.macro.TriggerMatcher
import com.personalmentor.app.domain.repository.MacroRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/** Starts/stops [MacroService] depending on whether any enabled macro has an automatic trigger. */
object MacroServiceController {
    fun sync(context: Context, macros: List<Macro>) {
        val needed = macros.any { m -> m.enabled && m.triggers.any { TriggerMatcher.isAutomatic(it) } }
        if (!needed) return // the service stops itself when nothing is left to listen for
        try {
            ContextCompat.startForegroundService(context, Intent(context, MacroService::class.java))
        } catch (e: Exception) {
            // Android refuses to start a foreground service from the background; it starts next time the app opens.
        }
    }
}

/** Starts the listener after a reboot so macros with a "Device Boot" trigger run. */
class MacroBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, MacroService::class.java).putExtra(MacroService.EXTRA_BOOT, true))
        } catch (e: Exception) {
            // Not allowed right now; the service starts when the app is next opened.
        }
    }
}

/**
 * Foreground service that listens for phone events (power, screen, Wi-Fi, airplane mode, app installs,
 * battery level) and a clock tick, and hands them to [MacroExecutor].
 */
@AndroidEntryPoint
class MacroService : Service() {

    @Inject lateinit var executor: MacroExecutor
    @Inject lateinit var repository: MacroRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false
    private var lastBattery = -1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> MacroEvent("power_connected")
                Intent.ACTION_POWER_DISCONNECTED -> MacroEvent("power_disconnected")
                Intent.ACTION_SCREEN_ON -> MacroEvent("screen_on")
                Intent.ACTION_SCREEN_OFF -> MacroEvent("screen_off")
                Intent.ACTION_USER_PRESENT -> MacroEvent("unlock")
                Intent.ACTION_PACKAGE_ADDED -> if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) null else MacroEvent("app_installed", extra = intent.data?.schemeSpecificPart.orEmpty())
                Intent.ACTION_PACKAGE_REMOVED -> if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) null else MacroEvent("app_removed", extra = intent.data?.schemeSpecificPart.orEmpty())
                "android.net.wifi.WIFI_STATE_CHANGED" -> when (intent.getIntExtra("wifi_state", -1)) {
                    3 -> MacroEvent("wifi_on") // WIFI_STATE_ENABLED
                    1 -> MacroEvent("wifi_off") // WIFI_STATE_DISABLED
                    else -> null
                }
                Intent.ACTION_AIRPLANE_MODE_CHANGED ->
                    MacroEvent(if (intent.getBooleanExtra("state", false)) "airplane_on" else "airplane_off")
                Intent.ACTION_BATTERY_CHANGED -> batteryEvent(intent)
                else -> null
            }
            if (event != null) scope.launch { executor.onEvent(event) }
        }
    }

    private fun batteryEvent(intent: Intent): MacroEvent? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return null
        val percent = level * 100 / scale
        val previous = lastBattery
        lastBattery = percent
        return if (previous < 0 || previous == percent) null else MacroEvent("battery", level = percent, previousLevel = previous)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (!started) {
            started = true
            register()
            scope.launch {
                while (true) {
                    executor.onEvent(MacroEvent("tick", time = LocalDateTime.now()))
                    delay(TICK_MS)
                }
            }
            scope.launch {
                repository.macros.collect { list ->
                    if (list.none { m -> m.enabled && m.triggers.any { TriggerMatcher.isAutomatic(it) } }) stopSelf()
                }
            }
        }
        if (intent?.getBooleanExtra(EXTRA_BOOT, false) == true) scope.launch { executor.onEvent(MacroEvent("boot")) }
        return START_STICKY
    }

    private fun register() {
        val system = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction("android.net.wifi.WIFI_STATE_CHANGED")
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val packages = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, receiver, system, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, receiver, packages, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun startAsForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Automations", NotificationManager.IMPORTANCE_LOW))
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Automations are active")
            .setContentText("Listening for the triggers of your macros")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_BOOT = "boot"
        private const val CHANNEL = "macro_service"
        private const val NOTIFICATION_ID = 4711
        private const val TICK_MS = 20_000L
    }
}

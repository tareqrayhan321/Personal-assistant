package com.personalmentor.app.data.macro

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.personalmentor.app.data.remote.ApiMessage
import com.personalmentor.app.data.remote.ChatCompletionChunk
import com.personalmentor.app.data.remote.ChatCompletionRequest
import com.personalmentor.app.data.remote.LlmApi
import com.personalmentor.app.data.remote.SseParser
import com.personalmentor.app.domain.macro.HttpResult
import com.personalmentor.app.domain.macro.MacroDevice
import com.personalmentor.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone side of macros. Uses only permissions that need no extra grant (notifications use the app's
 * existing one). Starting apps or web pages from the background is restricted by Android, so those two
 * actions work while this app is on screen.
 */
@Singleton
class AndroidMacroDevice @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llm: LlmApi,
    private val llmSettings: SettingsRepository,
    private val json: Json,
) : MacroDevice {

    private val main = Handler(Looper.getMainLooper())
    private val notificationIds = AtomicInteger(5000)

    // Own client: the app-wide one rewrites every request to the LLM server and adds its API key.
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    override fun now(): LocalDateTime = LocalDateTime.now()

    override fun batteryLevel(): Int =
        (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    override fun isCharging(): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged != 0
    }

    override fun isScreenOn(): Boolean = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    override fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    override fun isAirplaneMode(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    override fun isMusicActive(): Boolean = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive

    @SuppressLint("MissingPermission") // checked just below
    override fun notify(title: String, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Macros", NotificationManager.IMPORTANCE_DEFAULT))
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < 33
        if (!allowed) throw IllegalStateException("Notifications are not allowed for this app")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationIds.incrementAndGet(), notification)
    }

    override fun toast(text: String) {
        main.post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

    override fun vibrate(ms: Int) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun speak(text: String) {
        main.post {
            val engine = tts
            if (engine != null && ttsReady) {
                engine.speak(text, TextToSpeech.QUEUE_ADD, null, "macro")
            } else {
                pendingSpeech = text
                if (engine == null) {
                    tts = TextToSpeech(context) { status ->
                        ttsReady = status == TextToSpeech.SUCCESS
                        if (ttsReady) {
                            tts?.language = Locale.getDefault()
                            pendingSpeech?.let { tts?.speak(it, TextToSpeech.QUEUE_ADD, null, "macro") }
                        }
                        pendingSpeech = null
                    }
                }
            }
        }
    }

    override fun playSound() {
        RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
    }

    override fun setFlashlight(on: Boolean) {
        val cameras = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cameras.cameraIdList.firstOrNull { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: throw IllegalStateException("This phone has no flashlight")
        cameras.setTorchMode(id, on)
    }

    override fun setVolume(stream: String, percent: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val type = when (stream) {
            "Ring" -> AudioManager.STREAM_RING
            "Alarm" -> AudioManager.STREAM_ALARM
            "Notification" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }
        audio.setStreamVolume(type, audio.getStreamMaxVolume(type) * percent / 100, 0)
    }

    override suspend fun askAi(prompt: String): String = withContext(Dispatchers.IO) {
        val current = llmSettings.current()
        if (current.useFake) throw IllegalStateException("No AI is configured. Add an API key in Settings.")
        val request = ChatCompletionRequest(
            model = current.model,
            messages = listOf(ApiMessage(role = "system", content = AI_SYSTEM), ApiMessage(role = "user", content = prompt)),
            stream = true,
            temperature = 0.4,
        )
        val answer = StringBuilder()
        try {
            llm.chatCompletionStream(request).use { body ->
                val source = body.source()
                while (answer.length < MAX_AI_CHARS) {
                    val line = source.readUtf8Line() ?: break
                    when (val event = SseParser.parseLine(line)) {
                        SseParser.Event.Ignore -> continue
                        SseParser.Event.Done -> break
                        is SseParser.Event.Data ->
                            json.decodeFromString<ChatCompletionChunk>(event.payload).choices.firstOrNull()?.delta?.content?.let { answer.append(it) }
                    }
                }
            }
        } catch (e: IOException) {
            throw IllegalStateException("AI request failed: ${e.message ?: "network error"}")
        }
        answer.toString().trim().ifEmpty { throw IllegalStateException("The AI returned no answer") }
    }

    override fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun launchApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalStateException("App $packageName is not installed")
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun writeFile(name: String, text: String, append: Boolean) {
        val dir = File(context.filesDir, "macro_files").apply { mkdirs() }
        val target = File(dir, name)
        if (target.canonicalFile.parentFile != dir.canonicalFile) throw IllegalStateException("Invalid file name")
        if (append) target.appendText(text + "\n") else target.writeText(text)
    }

    override suspend fun httpRequest(method: String, url: String, body: String): HttpResult = withContext(Dispatchers.IO) {
        val target = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: throw IllegalStateException("Only https:// addresses are allowed")
        val requestBody = if (method == "POST") body.toRequestBody("text/plain; charset=utf-8".toMediaType()) else null
        val request = Request.Builder().url(target).method(method, requestBody).header("User-Agent", "PersonalMentor-Macro").build()
        try {
            http.newCall(request).execute().use { response ->
                HttpResult(response.code, response.body?.string().orEmpty().take(MAX_RESPONSE))
            }
        } catch (e: IOException) {
            throw IllegalStateException("Request failed: ${e.message ?: "network error"}")
        }
    }

    private companion object {
        const val CHANNEL = "macros"
        const val MAX_RESPONSE = 4000
        const val MAX_AI_CHARS = 6000
        const val AI_SYSTEM = "You are a helpful assistant inside a phone automation. Answer briefly and in plain text, without markdown."
    }
}

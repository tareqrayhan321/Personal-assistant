package com.personalmentor.app.data.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalmentor.app.domain.usecase.RescheduleRemindersUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-arms all pending reminders when:
 * - the device reboots (AlarmManager alarms are wiped), or
 * - the user grants exact-alarm access (earlier alarms were scheduled as inexact and are upgraded).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var rescheduleReminders: RescheduleRemindersUseCase

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // required by Hilt for injection
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                rescheduleReminders()
            } finally {
                pending.finish()
            }
        }
    }
}

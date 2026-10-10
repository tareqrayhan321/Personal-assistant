package com.personalmentor.app

import android.app.Application
import com.personalmentor.app.data.reminder.ReminderNotifier
import com.personalmentor.app.data.schedule.ScheduleNotifier
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PersonalMentorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderNotifier.createChannel(this)
        ScheduleNotifier.createChannels(this)
    }
}

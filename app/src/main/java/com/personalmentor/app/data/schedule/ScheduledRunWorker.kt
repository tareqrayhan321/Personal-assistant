package com.personalmentor.app.data.schedule

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduledRunDeps {
    fun runner(): ScheduledTaskRunner
}

/** Runs one scheduled task in the background (expedited, so it starts promptly even in Doze). */
class ScheduledRunWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId < 0) return Result.failure()
        EntryPointAccessors.fromApplication(applicationContext, ScheduledRunDeps::class.java).runner().run(taskId)
        return Result.success()
    }

    // Used on Android 11 and older, where expedited work runs as a foreground service.
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(FOREGROUND_ID, ScheduleNotifier.progressNotification(applicationContext))

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val FOREGROUND_ID = 4001
    }
}

object ScheduleWork {
    /** One run per task at a time: a request while that task is still running is dropped. */
    fun enqueue(context: Context, taskId: Long) {
        val request = OneTimeWorkRequestBuilder<ScheduledRunWorker>()
            .setInputData(workDataOf(ScheduledRunWorker.KEY_TASK_ID to taskId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_QUEUE)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("scheduled-run-$taskId", ExistingWorkPolicy.KEEP, request)
    }
}

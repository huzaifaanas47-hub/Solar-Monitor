package com.medicglobe.knoxsolar

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class KnoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)

        // Every ~15 minutes (Android's minimum): wake the server's cron worker if it
        // stopped, and raise a notification on grid outage or if logging is stuck.
        val request = PeriodicWorkRequestBuilder<GuardWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "knox-guard", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
}

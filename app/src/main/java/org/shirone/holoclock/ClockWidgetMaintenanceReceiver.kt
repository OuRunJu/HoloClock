package org.shirone.holoclock

import android.app.job.JobService
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import org.shirone.holoclock.HoloClockWidgetProvider
import android.appwidget.AppWidgetManager

class ClockWidgetMaintenanceReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ClockMaintenance"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT -> {
                restartWidgetServices(context)
            }
        }
    }

    private fun restartWidgetServices(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, HoloClockWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        if (appWidgetIds.isNotEmpty()) {
            val updateIntent = Intent(context, HoloClockWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(updateIntent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                jobScheduler.cancel(1001)

                val provider = HoloClockWidgetProvider()
                provider.startTicking(context)

            }
        } else {
        }
    }
}
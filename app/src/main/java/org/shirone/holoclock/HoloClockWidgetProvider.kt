package org.shirone.holoclock
import android.graphics.Color
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.*

class HoloClockWidgetProvider : AppWidgetProvider() {
    companion object {

        const val MODE_DARK = 1

        const val MODE_LIGHT = 2

        const val MODE_TRANSPARENT = 3

        const val MODE_GLASS = 4

    }
    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private var isRunning = false

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    )

    {
        if (appWidgetIds.isEmpty()) {

            return

        }


        if (handler == null) {
            handler = Handler(Looper.getMainLooper())
        }

        if (!isRunning) {
            startTicking(context)
        }

        updateAllWidgets(context, appWidgetManager, appWidgetIds)
    }

    fun startTicking(context: Context) {
        isRunning = true

        scheduleJob(context)

        runnable = Runnable {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HoloClockWidgetProvider::class.java))

            if (ids.isNotEmpty()) {
                updateAllWidgets(context, manager, ids)

                val now = System.currentTimeMillis()
                val delay = 60000 - (now % 60000)

                handler?.postDelayed(runnable!!, delay)
            } else {
                stopAlarm(context)
            }
        }
        handler?.post(runnable!!)
    }

    private fun scheduleJob(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

            jobScheduler.cancel(1001)

            val componentName = ComponentName(context, HoloClockJobService::class.java)
            val jobInfo = JobInfo.Builder(1001, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setPersisted(true)
                .setPeriodic(1000 * 60 * 1)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build()

            val result = jobScheduler.schedule(jobInfo)
            if (result == JobScheduler.RESULT_SUCCESS) {
                println("Job scheduled successfully")
            } else {
                println("Job scheduling failed")
            }
        }
    }

    private fun cancelJob(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(1001)
        }
    }

    private fun updateAllWidgets(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        val now = Date()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val weekFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val sharedPref = context.getSharedPreferences("HoloClockPrefs", Context.MODE_PRIVATE)
        val themeMode = sharedPref.getInt("theme_mode", MODE_DARK)
        val dateStr = dateFormat.format(now)
        val weekStr = weekFormat.format(now)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val options = manager.getAppWidgetOptions(id)
            val currentWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val finalText = if (currentWidth >= 200) {"$dateStr   $weekStr"
            } else
            {"$dateStr\n$weekStr"}
            views.setTextViewText(R.id.tv_date, finalText)
            views.setTextViewText(R.id.tv_time, timeFormat.format(now))

            when (themeMode) {
                MODE_DARK -> {
                    views.setTextColor(R.id.tv_time, Color.parseColor("#FF33B5E5"))
                    views.setTextColor(R.id.tv_date, Color.parseColor("#FFFFFFFF"))
                    views.setInt(R.id.widget_layout, "setBackgroundResource", R.drawable.widget_bg)
                }
                MODE_LIGHT -> {
                    views.setTextColor(R.id.tv_time, Color.parseColor("#FF47BBE6"))
                    views.setTextColor(R.id.tv_date, Color.parseColor("#FF333333"))
                    views.setInt(R.id.widget_layout, "setBackgroundResource", R.drawable.widget_bg_light)
                }
                MODE_TRANSPARENT -> {
                    views.setTextColor(R.id.tv_time, Color.parseColor("#DDFFFFFF"))
                    views.setTextColor(R.id.tv_date, Color.parseColor("#DDFFFFFF"))
                    views.setInt(R.id.widget_layout, "setBackgroundResource", R.drawable.widget_bg_trans)
                }
                MODE_GLASS -> {
                    views.setTextColor(R.id.tv_time, Color.parseColor("#BFFEFEFE"))
                    views.setTextColor(R.id.tv_date, Color.parseColor("#BFFEFEFE"))
                    views.setInt(R.id.widget_layout, "setBackgroundResource", R.drawable.widget_bg_glass)
                }
            }

            manager.updateAppWidget(id, views)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleJob(context)
        val sharedPref = context.getSharedPreferences("HoloClockPrefs", Context.MODE_PRIVATE)
        val themeMode = sharedPref.getInt("theme_mode", MODE_DARK)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, HoloClockWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        if (appWidgetIds.isNotEmpty()) {
            updateAllWidgets(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopAlarm(context)
        cancelJob(context)
    }

    private fun stopAlarm(context: Context) {
        handler?.removeCallbacks(runnable!!)
        handler = null
        runnable = null
        isRunning = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            if (isRunning) {
                stopAlarm(context)
                startTicking(context)
            }
        }
    }

    class HoloClockJobService : JobService() {
        override fun onStartJob(params: JobParameters?): Boolean {
            val context = applicationContext
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HoloClockWidgetProvider::class.java))

            if (ids.isNotEmpty()) {
                val intent = Intent(context, HoloClockWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }

            return false
        }

        override fun onStopJob(params: JobParameters?): Boolean {
            return false
        }
    }
}
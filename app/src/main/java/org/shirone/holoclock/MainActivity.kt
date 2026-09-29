package org.shirone.holoclock

import android.os.Bundle
import android.graphics.Color
import android.widget.RadioGroup
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.widget.RemoteViews
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.os.PowerManager
import androidx.appcompat.app.AlertDialog
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import org.shirone.holoclock.HoloClockWidgetProvider.Companion.MODE_GLASS

const val MODE_DARK = 1
const val MODE_LIGHT = 2
const val MODE_TRANSPARENT = 3

const val MODE_GLASS = 4

class MainActivity : AppCompatActivity() {

    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var btnGrant: Button
    private lateinit var tvStatus: TextView
    private lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        themeRadioGroup = findViewById(R.id.theme_radio_group)
        btnGrant = findViewById(R.id.btn_grant)
        btnGrant.setOnClickListener {
            startPermissionSetup()
        }
        tvStatus = findViewById(R.id.tv_status)
        sharedPref = getSharedPreferences("HoloClockPrefs", MODE_PRIVATE)

        checkFirstLaunch()

        val currentMode = sharedPref.getInt("theme_mode", MODE_DARK)
        when (currentMode) {
            MODE_DARK -> themeRadioGroup.check(R.id.rb_dark_mode)
            MODE_LIGHT -> themeRadioGroup.check(R.id.rb_light_mode)
            MODE_TRANSPARENT -> themeRadioGroup.check(R.id.rb_transparent_mode)
            MODE_GLASS -> themeRadioGroup.check(R.id.rb_glass_mode)
        }

        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.rb_dark_mode -> MODE_DARK
                R.id.rb_light_mode -> MODE_LIGHT
                R.id.rb_transparent_mode -> MODE_TRANSPARENT
                R.id.rb_glass_mode -> MODE_GLASS
                else -> MODE_DARK
            }

            sharedPref.edit().putInt("theme_mode", newMode).apply()
            updateWidgetColors(this, newMode)

            recreate()
        }
    }

    private fun applySavedTheme() {
        val themeMode = getSharedPreferences("HoloClockPrefs", MODE_PRIVATE)
            .getInt("theme_mode", MODE_DARK)

        when (themeMode) {
            MODE_DARK -> setTheme(R.style.Theme_HoloClock_Dark)
            MODE_LIGHT -> setTheme(R.style.Theme_HoloClock_Light)
            MODE_TRANSPARENT -> setTheme(R.style.Theme_HoloClock_Transparent)
            MODE_GLASS -> setTheme(R.style.Theme_HoloClock_Glass)
        }
    }

    private fun checkFirstLaunch() {
        val isFirstLaunch = sharedPref.getBoolean("is_first_launch", true)
        if (isFirstLaunch) {
            showPermissionGuide()
            sharedPref.edit().putBoolean("is_first_launch", false).apply()
        }
    }

    private fun showPermissionGuide() {
        btnGrant.visibility = Button.VISIBLE
        tvStatus.text = getString(R.string.status_need_permission)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_guide_title))
            .setMessage(getString(R.string.permission_guide_message))
            .setPositiveButton(getString(R.string.permission_guide_positive)) { _, _ -> startPermissionSetup() }
            .setNegativeButton(getString(R.string.permission_guide_negative)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun startPermissionSetup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, 1002)
                return
            }
        }
        showAutoStartGuide()
    }

    private fun showAutoStartGuide() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.autostart_guide_title))
            .setMessage(getString(R.string.autostart_guide_message))
            .setPositiveButton(getString(R.string.autostart_guide_positive)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndRefreshUI()
    }

    private fun checkBackgroundPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(packageName)
        }
        return true
    }

    private fun checkPermissionsAndRefreshUI() {
        val granted = checkBackgroundPermission()
        if (granted) {
            btnGrant.visibility = View.GONE
            tvStatus.text = getString(R.string.status_permission_granted)
            updateHoloWidget()
        } else {
            btnGrant.visibility = View.VISIBLE
            tvStatus.text = getString(R.string.status_need_permission)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            1002 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                        showAutoStartGuide()
                        checkPermissionsAndRefreshUI()
                    }
                }
            }
        }
    }

    private fun updateHoloWidget() {
        val currentMode = sharedPref.getInt("theme_mode", MODE_DARK)
        updateWidgetColors(this, currentMode)
    }

    private fun updateWidgetColors(context: Context, themeMode: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, HoloClockWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

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
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun updateAllWidgets(context: Context, themeMode: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, HoloClockWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            when (themeMode){
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
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
package com.primitives.stoicboard

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.primitives.stoicboard.data.SettingsRepository
import com.primitives.stoicboard.data.StoicSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    val settings: StateFlow<StoicSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoicSettings())

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    init {
        loadApps()
    }

    fun save(settings: StoicSettings) {
        viewModelScope.launch { repository.save(settings) }
    }

    fun loadApps() {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager

                val launcherPkgs = mutableSetOf<String>()
                pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
                ).forEach { launcherPkgs.add(it.activityInfo.packageName) }
                pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
                ).forEach { launcherPkgs.add(it.activityInfo.packageName) }

                val categorised = settings.value.safeZonePackages +
                    settings.value.messengerPackages +
                    settings.value.workAppPackages

                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { app ->
                        app.flags and ApplicationInfo.FLAG_SYSTEM == 0
                            || app.packageName in launcherPkgs
                            || app.packageName in categorised
                    }
                    .map { app ->
                        AppInfo(
                            packageName = app.packageName,
                            label = pm.getApplicationLabel(app).toString()
                        )
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }
        }
    }
}

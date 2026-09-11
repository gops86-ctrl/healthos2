package com.healthos.app.feature.data

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthos.app.data.local.database.HealthOSDatabase
import com.healthos.app.data.local.entity.ActivityEntity
import com.healthos.app.data.local.entity.RunningActivityEntity
import com.healthos.app.data.source.strava.StravaArchiveImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class StravaImportViewModel(application: Application) : AndroidViewModel(application) {
    private val database = HealthOSDatabase.getInstance(application)
    private val activityDao = database.activityDao()
    private val legacyDao = database.runningActivityDao()
    private val _state = MutableStateFlow(StravaImportState())
    val state: StateFlow<StravaImportState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            migrateLegacyRuns()
            refreshCount()
        }
    }

    fun importArchive(uri: Uri) {
        if (_state.value.importing) return
        _state.value = _state.value.copy(importing = true, progress = 0, progressStage = "Preparing archive…", processed = 0, progressTotal = 0, message = null)
        viewModelScope.launch {
            val result = StravaArchiveImporter(getApplication(), activityDao).importArchive(uri) { progress ->
                _state.value = _state.value.copy(
                    progress = progress.percent,
                    progressStage = progress.stage,
                    processed = progress.processed,
                    progressTotal = progress.total
                )
            }
            _state.value = _state.value.copy(
                importing = false,
                progress = if (result.error == null) 100 else _state.value.progress,
                progressStage = if (result.error == null) "Import complete" else "Import failed",
                imported = result.imported,
                skipped = result.skipped,
                total = activityDao.count(),
                message = result.error ?: buildSuccessMessage(result.imported, result.skipped)
            )
        }
    }

    private suspend fun migrateLegacyRuns() {
        val legacyRuns = legacyDao.getAll()
        if (legacyRuns.isEmpty()) return
        val importedAt = System.currentTimeMillis()
        val activities = legacyRuns.mapNotNull { run -> run.toCanonical(importedAt) }
        if (activities.isNotEmpty()) activityDao.insertAll(activities)
    }

    private fun RunningActivityEntity.toCanonical(importedAt: Long): ActivityEntity? {
        val recordedAt = parseLegacyDate(startedAt) ?: return null
        return ActivityEntity(activityType = "RUN", name = name, durationSeconds = movingTimeSeconds ?: elapsedTimeSeconds ?: 0L, distanceMeters = distanceMeters, averageHeartRate = averageHeartRateBpm?.toInt(), elevationGainMeters = elevationGainMeters, source = source, sourceRecordId = externalId, recordedAtMillis = recordedAt, importedAtMillis = importedAt)
    }

    private fun parseLegacyDate(value: String): Long? {
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for (pattern in patterns) runCatching { SimpleDateFormat(pattern, Locale.US).parse(value)?.time }.getOrNull()?.let { return it }
        return null
    }

    private suspend fun refreshCount() { _state.value = _state.value.copy(total = activityDao.count()) }

    private fun buildSuccessMessage(imported: Int, skipped: Int): String = when {
        imported == 0 && skipped > 0 -> "All $skipped activities were already imported."
        skipped > 0 -> "Imported $imported activities. Skipped $skipped duplicates."
        else -> "Imported $imported activities."
    }
}

data class StravaImportState(
    val importing: Boolean = false,
    val progress: Int = 0,
    val progressStage: String? = null,
    val processed: Int = 0,
    val progressTotal: Int = 0,
    val total: Int = 0,
    val imported: Int = 0,
    val skipped: Int = 0,
    val message: String? = null
)

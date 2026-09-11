package com.healthos.app.feature.data

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthos.app.data.source.hevy.HevySyncImporter
import com.healthos.app.data.source.myfitnesspal.MyFitnessPalSyncImporter
import com.healthos.app.domain.model.LabResult
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class GarminRange(val label: String, val key: String) {
    DAYS_7("7D", "7D"), DAYS_30("30D", "30D"), MONTHS_3("3M", "3M"), YEAR_1("1Y", "1Y"), ALL("All", "ALL")
}

private enum class HevySyncRange(val label: String, val key: String) {
    DAYS_7("7D", "7D"), DAYS_30("30D", "30D")
}

private enum class MfpSyncRange(val label: String, val days: Int) {
    DAYS_7("7D", 7), DAYS_30("30D", 30)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    viewModel: DataViewModel,
    onGarminSync: suspend (String, (Int, String) -> Unit) -> Int,
    onHevySync: suspend (String, (Int, String) -> Unit) -> HevySyncImporter.Result,
    onMyFitnessPalSync: suspend (String, (Int, String) -> Unit) -> MyFitnessPalSyncImporter.Result
) {
    val stravaViewModel: StravaImportViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val stravaState by stravaViewModel.state.collectAsState()
    val hevyState by viewModel.hevyState.collectAsState()
    val healthifyState by viewModel.healthifyState.collectAsState()
    val mfpState by viewModel.mfpState.collectAsState()
    val labResults = emptyList<LabResult>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var garminSyncing by remember { mutableStateOf(false) }
    var garminMessage by remember { mutableStateOf<String?>(null) }
    var garminError by remember { mutableStateOf(false) }
    var garminProgress by remember { mutableStateOf(0) }
    var garminStage by remember { mutableStateOf("Preparing Garmin sync…") }
    var selectedGarminRange by remember { mutableStateOf(GarminRange.DAYS_7) }
    var showHevySync by remember { mutableStateOf(false) }
    var showManualWeight by remember { mutableStateOf(false) }
    var showLabDialog by remember { mutableStateOf(false) }
    var labName by remember { mutableStateOf("") }
    var labValue by remember { mutableStateOf("") }
    var labUnit by remember { mutableStateOf("") }
    var labReference by remember { mutableStateOf("") }
    var labDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var labNameError by remember { mutableStateOf(false) }
    var labValueError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var mfpSyncing by remember { mutableStateOf(false) }
    var mfpSyncMessage by remember { mutableStateOf<String?>(null) }
    var mfpSyncError by remember { mutableStateOf(false) }
    var mfpSyncProgress by remember { mutableStateOf(0) }
    var mfpSyncStage by remember { mutableStateOf("Ready to sync MyFitnessPal") }
    var selectedMfpRange by remember { mutableStateOf(MfpSyncRange.DAYS_7) }

    fun resetLabForm() {
        labName = ""; labValue = ""; labUnit = ""; labReference = ""
        labDateMillis = System.currentTimeMillis(); labNameError = false; labValueError = false
    }

    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(stravaViewModel::importArchive) }
    val hevyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(viewModel::importHevyCsv) }
    val healthifyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(viewModel::importHealthifyCsv) }
    val mfpPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(viewModel::importMyFitnessPalCsv) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Data Sources", fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        Text("Import your existing health and fitness data into HealthOS.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Watch, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Garmin", fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text("Sync health metrics", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                Text("Choose how much Garmin history to sync. The app stores the imported history locally for charts.", fontSize = 14.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { GarminRange.values().forEach { range -> FilterChip(selected = selectedGarminRange == range, onClick = { selectedGarminRange = range }, label = { Text(range.label) }) } }
                Button(onClick = { scope.launch { garminSyncing = true; garminError = false; garminProgress = 0; garminStage = "Preparing Garmin sync…"; garminMessage = "Syncing Garmin ${selectedGarminRange.label}…"; try { val count = onGarminSync(selectedGarminRange.key) { progress, stage -> garminProgress = progress.coerceIn(0, 100); garminStage = stage }; garminProgress = 100; garminStage = "Garmin sync complete"; garminMessage = if (count > 0) "Garmin sync complete — $count metrics updated." else "Garmin sync completed, but no new metrics were returned." } catch (error: Throwable) { garminError = true; garminStage = "Garmin sync stopped"; garminMessage = error.message?.takeIf { it.isNotBlank() } ?: "Garmin sync failed." } finally { garminSyncing = false } } }, enabled = !garminSyncing, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text(if (garminSyncing) "Syncing…" else "Sync Garmin") }
                if (garminSyncing || garminProgress > 0) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(garminStage, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("$garminProgress%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }; LinearProgressIndicator(progress = { garminProgress / 100f }, modifier = Modifier.fillMaxWidth()) }
                garminMessage?.let { Text(it, fontSize = 13.sp, color = if (garminError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Science, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Lab Results", fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text("Enter blood test and laboratory values manually", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                Text("Record the result exactly as shown on your lab report. You can keep multiple results for the same test over time.", fontSize = 14.sp)
                Button(onClick = { resetLabForm(); showLabDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add lab result") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Restaurant, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("MyFitnessPal", fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text("Nutrition history import & live sync", color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (mfpState.imported > 0 || mfpSyncMessage?.contains("complete", ignoreCase = true) == true) Icon(Icons.Default.CheckCircle, "MyFitnessPal synced") }
                Text("Import the MyFitnessPal Nutrition Summary CSV for archive history, or sync recent nutrition directly from MyFitnessPal. Live sync replaces existing MyFitnessPal entries for the synced dates so CSV and live data are not double-counted.", fontSize = 14.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { mfpPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "application/octet-stream")) }, enabled = !mfpState.importing && !mfpSyncing, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(if (mfpState.importing) "Importing…" else "Import CSV") }
                    Button(onClick = { scope.launch { mfpSyncing = true; mfpSyncError = false; mfpSyncProgress = 0; mfpSyncStage = "Starting MyFitnessPal sync…"; mfpSyncMessage = null; try { val result = onMyFitnessPalSync(selectedMfpRange.days.toString()) { progress, stage -> mfpSyncProgress = progress.coerceIn(0, 100); mfpSyncStage = stage }; mfpSyncProgress = 100; if (result.error != null) { mfpSyncError = true; mfpSyncStage = "MyFitnessPal sync failed"; mfpSyncMessage = result.error } else { mfpSyncStage = "MyFitnessPal sync complete"; mfpSyncMessage = "${result.imported} nutrition entries synced${if (result.skipped > 0) "; ${result.skipped} skipped" else ""}." } } catch (error: Throwable) { mfpSyncError = true; mfpSyncStage = "MyFitnessPal sync failed"; mfpSyncMessage = error.message?.takeIf { it.isNotBlank() } ?: "MyFitnessPal sync failed." } finally { mfpSyncing = false } } }, enabled = !mfpState.importing && !mfpSyncing, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text(if (mfpSyncing) "Syncing…" else "Sync MFP") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Live window", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MfpSyncRange.values().forEach { range -> FilterChip(selected = selectedMfpRange == range, onClick = { if (!mfpSyncing) selectedMfpRange = range }, label = { Text(range.label) }) }
                }
                if (mfpState.importing || mfpState.progress > 0) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(mfpState.stage ?: "Importing…", fontSize = 13.sp); Text("${mfpState.progress}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }; LinearProgressIndicator(progress = { mfpState.progress / 100f }, modifier = Modifier.fillMaxWidth()); if (mfpState.total > 0) Text("${mfpState.processed} / ${mfpState.total}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (mfpSyncing || mfpSyncProgress > 0) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(mfpSyncStage, fontSize = 13.sp, color = if (mfpSyncError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant); Text("${mfpSyncProgress}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }; LinearProgressIndicator(progress = { mfpSyncProgress / 100f }, modifier = Modifier.fillMaxWidth()) }
                mfpState.message?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                mfpSyncMessage?.let { Text(it, fontSize = 13.sp, color = if (mfpSyncError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.MonitorWeight, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("HealthifyMe", fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text("Smart Scale weight import", color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (healthifyState.imported > 0) Icon(Icons.Default.CheckCircle, "HealthifyMe imported") }
                Text("Import your HealthifyMe weight export. HealthOS stores the weight history locally and calculates personalized body-composition estimates from it.", fontSize = 14.sp)
                Button(onClick = { healthifyPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "application/octet-stream")) }, enabled = !healthifyState.importing, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(if (healthifyState.importing) "Importing…" else "Import HealthifyMe data") }
                if (healthifyState.importing || healthifyState.progress > 0) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(healthifyState.stage ?: "Importing…", fontSize = 13.sp); Text("${healthifyState.progress}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }; LinearProgressIndicator(progress = { healthifyState.progress / 100f }, modifier = Modifier.fillMaxWidth()); if (healthifyState.total > 0) Text("${healthifyState.processed} / ${healthifyState.total}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                healthifyState.message?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.MonitorWeight, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Manual Weight", fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text("Log a weight reading", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                Text("Log your weight directly when you do not have a HealthifyMe reading. The entry is stored as a manual body measurement and feeds the internal body-composition estimate.", fontSize = 14.sp)
                Button(onClick = { showManualWeight = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Log weight") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Strava", fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text("History import", color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (stravaState.total > 0) Icon(Icons.Default.CheckCircle, "Strava imported") }
                Text("Request your personal Strava archive, then select the ZIP here. HealthOS processes the archive locally and stores your activity history.", fontSize = 14.sp)
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.strava.com/"))) }, modifier = Modifier.fillMaxWidth()) { Text("Open Strava website") }
                Button(onClick = { archivePicker.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !stravaState.importing, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(if (stravaState.importing) "Importing…" else "Import Strava archive") }
                if (stravaState.importing) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stravaState.progressStage ?: "Importing…", fontSize = 13.sp); Text("${stravaState.progress}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }; LinearProgressIndicator(progress = { stravaState.progress / 100f }, modifier = Modifier.fillMaxWidth()); if (stravaState.progressTotal > 0) Text("${stravaState.processed} / ${stravaState.progressTotal}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (stravaState.total > 0) Text("${stravaState.total} activities stored locally", fontWeight = FontWeight.Medium)
                stravaState.message?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.FitnessCenter, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Hevy", fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text("Strength workout import", color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (hevyState.imported > 0) Icon(Icons.Default.CheckCircle, "Hevy imported") }
                Text("Sync recent Hevy workouts from your connected account, or import your workout_data.csv archive. Strength workouts, exercises and sets are stored locally in HealthOS.", fontSize = 14.sp)
                Button(onClick = { showHevySync = true }, enabled = true, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text("Sync Hevy") }
                Button(onClick = { hevyPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "application/octet-stream")) }, enabled = !hevyState.importing, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(if (hevyState.importing) "Importing…" else "Import Hevy CSV") }
                if (hevyState.importing || hevyState.progress > 0) { LinearProgressIndicator(progress = { hevyState.progress / 100f }, modifier = Modifier.fillMaxWidth()); hevyState.stage?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                hevyState.message?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Text("HealthifyMe estimates are clearly labeled and are based on your historical Smart Scale readings; they are not HealthifyMe's proprietary BIA calculation.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("On Strava, open Settings → My Account → Download your account → Get Started, then request the archive. The ZIP can be imported here.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (showHevySync) {
        HevySyncDialog(onDismiss = { showHevySync = false }, onSync = onHevySync)
    }
    if (showManualWeight) {
        ManualWeightDialog(viewModel = viewModel, onDismiss = { showManualWeight = false })
    }

    if (showLabDialog) {
        AlertDialog(
            onDismissRequest = { showLabDialog = false },
            title = { Text("Add lab result") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = labName, onValueChange = { labName = it; labNameError = false }, label = { Text("Test name") }, placeholder = { Text("e.g. Ferritin") }, isError = labNameError, supportingText = { if (labNameError) Text("Enter a test name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = labValue, onValueChange = { labValue = it; labValueError = false }, label = { Text("Value") }, placeholder = { Text("e.g. 85") }, isError = labValueError, supportingText = { if (labValueError) Text("Enter a numeric value") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = labUnit, onValueChange = { labUnit = it }, label = { Text("Unit") }, placeholder = { Text("e.g. ng/mL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = labReference, onValueChange = { labReference = it }, label = { Text("Reference range") }, placeholder = { Text("e.g. 30–100") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) { Text("Test date: ${DateFormat.getDateInstance().format(Date(labDateMillis))}") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    labNameError = labName.isBlank()
                    labValueError = labValue.trim().toDoubleOrNull() == null
                    if (!labNameError && !labValueError) { viewModel.saveLabResult(labName, labValue, labUnit, labReference, labDateMillis); showLabDialog = false }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showLabDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = labDateMillis)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { pickerState.selectedDateMillis?.let { labDateMillis = it }; showDatePicker = false }) { Text("Done") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun HevySyncDialog(
    onDismiss: () -> Unit,
    onSync: suspend (String, (Int, String) -> Unit) -> HevySyncImporter.Result
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(HevySyncRange.DAYS_7) }
    var syncing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var stage by remember { mutableStateOf("Choose a sync window") }
    var resultText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!syncing) onDismiss() },
        title = { Text("Sync Hevy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Fetch recent Hevy workouts, deduplicate them against local records, then update the canonical HealthOS snapshot.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { HevySyncRange.values().forEach { range -> FilterChip(selected = selected == range, onClick = { if (!syncing) selected = range }, label = { Text(range.label) }) } }
                if (syncing || resultText != null) {
                    Text(stage, style = MaterialTheme.typography.bodySmall, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
                resultText?.let { Text(it, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = {
            Button(enabled = !syncing, onClick = {
                scope.launch {
                    syncing = true; error = false; progress = 0; resultText = null; stage = "Starting Hevy sync…"
                    try {
                        val result = onSync(selected.key) { p, s -> progress = p.coerceIn(0, 100); stage = s }
                        progress = 100
                        if (result.error != null) { error = true; stage = "Sync failed"; resultText = result.error }
                        else { stage = "Sync complete"; resultText = "${result.imported} added, ${result.skipped} already present." }
                    } catch (e: Throwable) { error = true; stage = "Sync failed"; resultText = e.message ?: "Unable to sync Hevy." }
                    finally { syncing = false }
                }
            }) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text("Sync") }
        },
        dismissButton = { TextButton(enabled = !syncing, onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun LabDataSourceRow(result: LabResult, onDelete: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 11.dp, bottom = 11.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(result.testName, fontWeight = FontWeight.SemiBold)
                Text(DateFormat.getDateInstance().format(Date(result.provenance.recordedAtMillis)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                result.referenceRange?.let { Text("Ref: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("${result.value} ${result.unit}".trim(), fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${result.testName}") }
        }
    }
}

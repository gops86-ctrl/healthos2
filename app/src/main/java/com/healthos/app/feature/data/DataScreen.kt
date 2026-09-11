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
import com.healthos.app.domain.model.LabResult
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private enum class GarminRange(val label: String, val key: String) {
    DAYS_7("7D", "7D"), DAYS_30("30D", "30D"), MONTHS_3("3M", "3M"), YEAR_1("1Y", "1Y"), ALL("All", "ALL")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(viewModel: DataViewModel, onGarminSync: suspend (String, (Int, String) -> Unit) -> Int) {
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
    var showLabDialog by remember { mutableStateOf(false) }
    var labName by remember { mutableStateOf("") }
    var labValue by remember { mutableStateOf("") }
    var labUnit by remember { mutableStateOf("") }
    var labReference by remember { mutableStateOf("") }
    var labDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var labNameError by remember { mutableStateOf(false) }
    var labValueError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

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
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Restaurant, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("MyFitnessPal", fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text("Nutrition history import", color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (mfpState.imported > 0) Icon(Icons.Default.CheckCircle, "MyFitnessPal imported") }
                Text("Import the MyFitnessPal Nutrition Summary CSV. HealthOS stores meal-level calories, macros and available micronutrients locally, then aggregates them by day in Nutrition.", fontSize = 14.sp)
                Button(onClick = { mfpPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "application/octet-stream")) }, enabled = !mfpState.importing, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(if (mfpState.importing) "Importing…" else "Import MyFitnessPal CSV") }
                if (mfpState.importing || mfpState.progress > 0) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(mfpState.stage ?: "Importing…", fontSize = 13.sp); Text("${mfpState.progress}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }; LinearProgressIndicator(progress = { mfpState.progress / 100f }, modifier = Modifier.fillMaxWidth()); if (mfpState.total > 0) Text("${mfpState.processed} / ${mfpState.total}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                mfpState.message?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                Text("Import your Hevy workout_data.csv. Strength workouts, exercises and sets are stored locally in HealthOS.", fontSize = 14.sp)
                Button(onClick = { hevyPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "application/octet-stream")) }, enabled = !hevyState.importing, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(if (hevyState.importing) "Importing…" else "Import Hevy CSV") }
                if (hevyState.importing || hevyState.progress > 0) { LinearProgressIndicator(progress = { hevyState.progress / 100f }, modifier = Modifier.fillMaxWidth()); hevyState.stage?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                hevyState.message?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Text("HealthifyMe estimates are clearly labeled and are based on your historical Smart Scale readings; they are not HealthifyMe's proprietary BIA calculation.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("On Strava, open Settings → My Account → Download your account → Get Started, then request the archive. The ZIP can be imported here.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

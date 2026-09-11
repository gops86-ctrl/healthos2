package com.healthos.app.feature.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(profileViewModel: ProfileViewModel, onBack: () -> Unit) {
    val saved by profileViewModel.profile.collectAsState()
    var name by remember(saved) { mutableStateOf(saved.name) }
    var gender by remember(saved) { mutableStateOf(saved.gender) }
    var height by remember(saved) { mutableStateOf(saved.heightCm) }
    var dob by remember(saved) { mutableStateOf(saved.dateOfBirth) }
    var photoUri by remember(saved) { mutableStateOf(saved.photoUri) }
    var genderExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            photoUri = it.toString()
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Profile") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            Box(contentAlignment = Alignment.BottomEnd) {
                if (photoUri.isNullOrBlank()) Icon(Icons.Default.AccountCircle, null, Modifier.size(104.dp), tint = MaterialTheme.colorScheme.primary)
                else AsyncImage(Uri.parse(photoUri), null, Modifier.size(104.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                SmallFloatingActionButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.CameraAlt, "Change photo", Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            ExposedDropdownMenuBox(expanded = genderExpanded, onExpandedChange = { genderExpanded = !genderExpanded }) {
                OutlinedTextField(gender, {}, Modifier.fillMaxWidth().menuAnchor(), label = { Text("Gender") }, readOnly = true, singleLine = true)
                ExposedDropdownMenu(expanded = genderExpanded, onDismissRequest = { genderExpanded = false }) {
                    listOf("Male", "Female", "Other", "Prefer not to say").forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { gender = option; genderExpanded = false }) }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(height, { height = it }, Modifier.fillMaxWidth(), label = { Text("Height (cm)") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(dob, {}, Modifier.fillMaxWidth(), label = { Text("Date of birth") }, placeholder = { Text("Select date") }, readOnly = true, singleLine = true, leadingIcon = { Icon(Icons.Default.CalendarMonth, null) })
                Spacer(Modifier.matchParentSize().clickable { showDatePicker = true })
            }
            Spacer(Modifier.height(28.dp))
            Button(onClick = { profileViewModel.save(com.healthos.app.data.local.UserProfile(name, gender, height, dob, photoUri)); onBack() }, Modifier.fillMaxWidth()) { Text("Save profile") }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis -> dob = DateTimeFormatter.ofPattern("dd MMM yyyy").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())) }
                showDatePicker = false
            }) { Text("Done") }
        }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }) {
            DatePicker(state = datePickerState)
        }
    }
}

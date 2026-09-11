package com.healthos.app.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.healthos.app.core.common.HealthOSViewModelFactory
import com.healthos.app.data.local.ProfilePreferences
import com.healthos.app.domain.model.ActivityType
import com.healthos.app.domain.model.MetricType
import com.healthos.app.domain.repository.HealthRepository
import com.healthos.app.feature.data.DataScreen
import com.healthos.app.feature.fitness.*
import com.healthos.app.feature.health.HealthScreen
import com.healthos.app.feature.health.LabDetailScreen
import com.healthos.app.feature.health.WeightDetailScreen
import com.healthos.app.feature.metric.*
import com.healthos.app.feature.nutrition.*
import com.healthos.app.feature.overview.LocalProfileClick
import com.healthos.app.feature.overview.LocalUserProfile
import com.healthos.app.feature.overview.LocalWhatChangedClick
import com.healthos.app.feature.overview.OverviewScreen
import com.healthos.app.feature.overview.WhatChangedScreen
import com.healthos.app.feature.profile.ProfileScreen
import com.healthos.app.feature.profile.ProfileViewModel
import com.healthos.app.feature.profile.ProfileViewModelFactory
import com.healthos.app.feature.strength.*

private const val METRIC_ROUTE = "metric/{type}"
private const val WORKOUT_ROUTE = "workout/{id}"
private const val ACTIVITY_ROUTE = "activity/{id}"
private const val NUTRITION_DAY_ROUTE = "nutrition/{date}"
private const val LAB_ROUTE = "lab/{id}"

@Composable
fun HealthOSNavHost(repository: HealthRepository, onGarminSync: suspend (String, (Int, String) -> Unit) -> Int, onHevySync: suspend (String, (Int, String) -> Unit) -> com.healthos.app.data.source.hevy.HevySyncImporter.Result, onDeleteNutritionDay: suspend (Long) -> Unit) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val metrics by repository.observeLatestMetrics().collectAsState(initial = emptyList())
    val nutritionEntries by repository.observeNutritionEntries().collectAsState(initial = emptyList())
    val bodyMeasurements by repository.observeBodyMeasurements().collectAsState(initial = emptyList())
    val labResults by repository.observeLabResults().collectAsState(initial = emptyList())
    val factory = remember(repository) { HealthOSViewModelFactory(repository) }
    val profilePreferences = remember(context) { ProfilePreferences(context) }
    val profileViewModel = viewModel<ProfileViewModel>(factory = remember(profilePreferences) { ProfileViewModelFactory(profilePreferences) })
    val profile by profileViewModel.profile.collectAsState()
    val overviewViewModel = viewModel<com.healthos.app.feature.overview.OverviewViewModel>(factory = factory)
    val items = listOf("overview" to "Overview", "fitness" to "Fitness", "health" to "Health", "nutrition" to "Nutrition", "data" to "More")

    Scaffold(bottomBar = {
        val current = nav.currentBackStackEntryAsState().value?.destination?.route
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) { items.forEach { (route, label) ->
            val icon = when (route) { "overview" -> Icons.Default.Home; "fitness" -> Icons.Default.DirectionsRun; "health" -> Icons.Default.FavoriteBorder; "nutrition" -> Icons.Default.Restaurant; else -> Icons.Default.MoreHoriz }
            NavigationBarItem(selected = current == route, onClick = { nav.navigate(route) { launchSingleTop = true } }, icon = { Icon(icon, label) }, label = { Text(label) })
        } }
    }) { padding ->
        NavHost(nav, "overview", modifier = Modifier.padding(padding)) {
            composable("overview") { CompositionLocalProvider(LocalWhatChangedClick provides { nav.navigate("what_changed") }, LocalProfileClick provides { nav.navigate("profile") }, LocalUserProfile provides profile) { OverviewScreen(onMetricClick = { nav.navigate("metric/${it.type.name}") }, onWeightClick = { nav.navigate("weight") }, onNutritionClick = { nav.navigate("nutrition") }, onActivityClick = { nav.navigate("activity/${it.id}") }, onWorkoutClick = { nav.navigate("workout/${it.id}") }, viewModel = overviewViewModel) } }
            composable("profile") { ProfileScreen(profileViewModel) { nav.popBackStack() } }
            composable("what_changed") { WhatChangedScreen(viewModel = overviewViewModel, onBack = { nav.popBackStack() }, onMetricClick = { nav.navigate("metric/${it.type.name}") }, onWeightClick = { nav.navigate("weight") }, onNutritionClick = { nav.navigate("nutrition") }) }
            composable("fitness") { val vm = viewModel<FitnessViewModel>(factory = factory); FitnessScreen(vm) { activity -> if (activity.activityType == ActivityType.STRENGTH && activity.provenance.source.name == "HEVY") nav.navigate("workout/${-activity.id}") else nav.navigate("activity/${activity.id}") } }
            composable("health") { HealthScreen(metrics, bodyMeasurements, labResults, { nav.navigate("metric/${it.type.name}") }, { nav.navigate("weight") }, { nav.navigate("lab/${it.id}") }) }
            composable("weight") { WeightDetailScreen(bodyMeasurements, { nav.popBackStack() }, { repository.deleteBodyMeasurement(it) }) }
            composable(LAB_ROUTE) { entry -> entry.arguments?.getString("id")?.toLongOrNull()?.let { id -> labResults.firstOrNull { it.id == id }?.let { result -> LabDetailScreen(result, { nav.popBackStack() }, { name, value, unit, ref, date -> repository.updateLabResult(id, name, value, unit, ref, date) }, { repository.deleteLabResult(id) }) } } }
            composable("nutrition") { NutritionScreen(metrics, nutritionEntries, { nav.navigate("metric/${it.type.name}") }, { nav.navigate("nutrition/$it") }) }
            composable(NUTRITION_DAY_ROUTE) { entry -> entry.arguments?.getString("date")?.toLongOrNull()?.let { date -> NutritionDetailScreen(date, nutritionEntries, { nav.popBackStack() }, onDeleteNutritionDay) } }
            composable("data") { val vm = viewModel<com.healthos.app.feature.data.DataViewModel>(factory = factory); DataScreen(vm, onGarminSync, onHevySync) }
            composable(METRIC_ROUTE) { entry -> val type = entry.arguments?.getString("type")?.let { runCatching { MetricType.valueOf(it) }.getOrNull() }; val metric = metrics.firstOrNull { it.type == type }; if (metric != null && type != null) { val vm = viewModel<MetricDetailViewModel>(factory = remember(repository, type) { MetricDetailViewModelFactory(repository, type) }); MetricDetailScreen(metric, vm) { nav.popBackStack() } } }
            composable(WORKOUT_ROUTE) { entry -> entry.arguments?.getString("id")?.toLongOrNull()?.let { id -> val vm = viewModel<WorkoutDetailViewModel>(factory = remember(repository, id) { WorkoutDetailViewModelFactory(repository, id) }); WorkoutDetailScreen(vm) { nav.popBackStack() } } }
            composable(ACTIVITY_ROUTE) { entry -> entry.arguments?.getString("id")?.toLongOrNull()?.let { id -> val vm = viewModel<ActivityDetailViewModel>(factory = remember(repository, id) { ActivityDetailViewModelFactory(repository, id) }); ActivityDetailScreen(vm) { nav.popBackStack() } } }
        }
    }
}

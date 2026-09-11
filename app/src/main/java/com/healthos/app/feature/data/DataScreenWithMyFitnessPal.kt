package com.healthos.app.feature.data

import androidx.compose.runtime.Composable
import com.healthos.app.data.source.hevy.HevySyncImporter
import com.healthos.app.data.source.myfitnesspal.MyFitnessPalSyncImporter

@Composable
fun DataScreenWithMyFitnessPal(
    viewModel: DataViewModel,
    onGarminSync: suspend (String, (Int, String) -> Unit) -> Int,
    onHevySync: suspend (String, (Int, String) -> Unit) -> HevySyncImporter.Result,
    onMyFitnessPalSync: suspend (String, (Int, String) -> Unit) -> MyFitnessPalSyncImporter.Result
) {
    DataScreen(
        viewModel = viewModel,
        onGarminSync = onGarminSync,
        onHevySync = onHevySync,
        onMyFitnessPalSync = onMyFitnessPalSync
    )
}

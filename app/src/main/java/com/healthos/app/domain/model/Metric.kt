package com.healthos.app.domain.model

enum class MetricType(val label: String) {
    VO2_MAX("VO₂ Max"), RESTING_HR("Resting HR"), HRV("HRV"), SLEEP("Sleep"),
    STRESS("Stress"), STEPS("Steps"), WEIGHT("Weight"), CALORIES("Calories"),
    PROTEIN("Protein"), TRAINING_LOAD("Training Load"), VITAMIN_D("Vitamin D")
}

enum class DataSource(val label: String) {
    GARMIN("Garmin"), STRAVA("Strava"), HEVY("Hevy"), HEALTHIFYME("HealthifyMe"),
    MYFITNESSPAL("MyFitnessPal"), LABS("Labs"), MANUAL("Manual")
}

data class HealthMetric(
    val type: MetricType,
    val value: String,
    val delta: String,
    val source: DataSource,
    val recordedAtMillis: Long,
    val importedAtMillis: Long,
    val sourceRecordId: String? = null
)

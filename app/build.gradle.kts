plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val garminApiBaseUrl = project.findProperty("GARMIN_API_BASE_URL")?.toString()
    ?.trimEnd('/')
    ?.ifBlank { "https://healthos-ahqs.onrender.com" }
    ?: "https://healthos-ahqs.onrender.com"
val garminApiBaseUrlEscaped = garminApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")
val garminApiKey = project.findProperty("GARMIN_API_KEY")?.toString().orEmpty()
val garminApiKeyEscaped = garminApiKey.replace("\\", "\\\\").replace("\"", "\\\"")
val healthApiBaseUrl = project.findProperty("HEALTHOS_API_BASE_URL")?.toString()
    ?.trimEnd('/')
    ?.ifBlank { garminApiBaseUrl }
    ?: garminApiBaseUrl
val healthApiBaseUrlEscaped = healthApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")
val healthApiKey = project.findProperty("HEALTHOS_API_KEY")?.toString().orEmpty().ifBlank { garminApiKey }
val healthApiKeyEscaped = healthApiKey.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.healthos.app"
    compileSdk = 36
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    defaultConfig {
        applicationId = "com.healthos.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "GARMIN_API_BASE_URL", "\"$garminApiBaseUrlEscaped\"")
        buildConfigField("String", "GARMIN_API_KEY", "\"$garminApiKeyEscaped\"")
        buildConfigField("String", "HEALTHOS_API_BASE_URL", "\"$healthApiBaseUrlEscaped\"")
        buildConfigField("String", "HEALTHOS_API_KEY", "\"$healthApiKeyEscaped\"")
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

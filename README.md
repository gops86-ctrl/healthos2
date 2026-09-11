# HealthOS Android V1.4

Native Android foundation for the HealthOS personal athlete/health data platform.

## Stack
- Kotlin
- Jetpack Compose + Material 3
- Room for canonical, local-first persistence
- DataStore for settings

## Open
Open this folder in Android Studio, let Gradle sync, and run the `app` configuration on an emulator or Android device.

## Architecture

The codebase uses one modular architecture rooted at `com.healthos.app`:

```text
core/       navigation, shared UI, and app wiring
data/       Room entities, DAOs, repositories, and future provider sources
domain/     provider-neutral models and repository contracts
feature/    independently maintainable Compose screens and view models
```

`HealthOSDatabase` is the canonical local data store. The dashboard observes data through
`HealthRepository` and `OverviewViewModel`; composables do not query Room or provider APIs.

On first launch `RoomHealthRepository` adds demonstration samples to an empty database. These
samples follow the same Room → repository → domain → ViewModel pathway intended for imported
provider data, and retain a source record ID plus recorded and import timestamps.

Future Garmin, Strava, Hevy, HealthifyMe, and MyFitnessPal connectors belong in
`data/source/<provider>` and must normalize their data before writing it to Room.

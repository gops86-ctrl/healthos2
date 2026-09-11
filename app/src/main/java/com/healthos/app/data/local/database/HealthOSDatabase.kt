package com.healthos.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.healthos.app.data.local.dao.ActivityDao
import com.healthos.app.data.local.dao.BodyMeasurementDao
import com.healthos.app.data.local.dao.ExerciseDao
import com.healthos.app.data.local.dao.HealthMetricDao
import com.healthos.app.data.local.dao.LabResultDao
import com.healthos.app.data.local.dao.NutritionEntryDao
import com.healthos.app.data.local.dao.RunningActivityDao
import com.healthos.app.data.local.dao.StrengthWorkoutDao
import com.healthos.app.data.local.dao.WorkoutSetDao
import com.healthos.app.data.local.entity.*

@Database(
    entities = [HealthMetricEntity::class, ActivityEntity::class, StrengthWorkoutEntity::class, ExerciseEntity::class, WorkoutSetEntity::class, NutritionEntryEntity::class, BodyMeasurementEntity::class, ImportedFileEntity::class, LabResultEntity::class, RunningActivityEntity::class],
    version = 10,
    exportSchema = false
)
abstract class HealthOSDatabase : RoomDatabase() {
    abstract fun healthMetricDao(): HealthMetricDao
    abstract fun activityDao(): ActivityDao
    abstract fun strengthWorkoutDao(): StrengthWorkoutDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun nutritionEntryDao(): NutritionEntryDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao
    abstract fun labResultDao(): LabResultDao
    abstract fun runningActivityDao(): RunningActivityDao

    companion object {
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE activities ADD COLUMN elapsedDurationSeconds INTEGER")
                database.execSQL("ALTER TABLE activities ADD COLUMN maxHeartRate INTEGER")
                database.execSQL("ALTER TABLE activities ADD COLUMN averageSpeedMps REAL")
                database.execSQL("ALTER TABLE activities ADD COLUMN calories REAL")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) { override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("ALTER TABLE activities ADD COLUMN routePoints TEXT") } }
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN meal TEXT")
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN saturatedFatGrams REAL")
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN polyunsaturatedFatGrams REAL")
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN monounsaturatedFatGrams REAL")
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN transFatGrams REAL")
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN cholesterolMg REAL")
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN sodiumMg REAL")
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN potassiumMg REAL")
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN fiberGrams REAL")
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN sugarGrams REAL")
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN vitaminAPercent REAL")
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN vitaminCPercent REAL")
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN calciumPercent REAL")
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN ironPercent REAL")
                database.execSQL("ALTER TABLE nutrition_entries ADD COLUMN note TEXT")
            }
        }
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM lab_results WHERE sourceRecordId LIKE 'demo-%'")
            }
        }

        fun create(context: Context): HealthOSDatabase = Room.databaseBuilder(context.applicationContext, HealthOSDatabase::class.java, "healthos.db")
            .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

        fun getInstance(context: Context): HealthOSDatabase = create(context)
    }
}

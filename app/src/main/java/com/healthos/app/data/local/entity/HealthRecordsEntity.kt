package com.healthos.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "nutrition_entries", indices = [Index(value = ["source", "sourceRecordId"], unique = true)])
data class NutritionEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val calories: Double?,
    val proteinGrams: Double?,
    val carbohydrateGrams: Double?,
    val fatGrams: Double?,
    val meal: String? = null,
    val saturatedFatGrams: Double? = null,
    val polyunsaturatedFatGrams: Double? = null,
    val monounsaturatedFatGrams: Double? = null,
    val transFatGrams: Double? = null,
    val cholesterolMg: Double? = null,
    val sodiumMg: Double? = null,
    val potassiumMg: Double? = null,
    val fiberGrams: Double? = null,
    val sugarGrams: Double? = null,
    val vitaminAPercent: Double? = null,
    val vitaminCPercent: Double? = null,
    val calciumPercent: Double? = null,
    val ironPercent: Double? = null,
    val note: String? = null,
    val source: String,
    val sourceRecordId: String,
    val recordedAtMillis: Long,
    val importedAtMillis: Long
)

@Entity(tableName = "body_measurements", indices = [Index(value = ["source", "sourceRecordId"], unique = true)])
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measurementType: String,
    val value: Double,
    val unit: String,
    val source: String,
    val sourceRecordId: String,
    val recordedAtMillis: Long,
    val importedAtMillis: Long
)

@Entity(
    tableName = "lab_results",
    foreignKeys = [ForeignKey(
        entity = ImportedFileEntity::class,
        parentColumns = ["id"],
        childColumns = ["importedFileId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index(value = ["source", "sourceRecordId"], unique = true), Index("importedFileId")]
)
data class LabResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val testName: String,
    val value: Double,
    val unit: String,
    val referenceRange: String?,
    val source: String,
    val sourceRecordId: String,
    val recordedAtMillis: Long,
    val importedAtMillis: Long,
    val importedFileId: Long? = null
)

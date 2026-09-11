package com.healthos.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_attributes", indices = [Index(value = ["attributeType"], unique = true)])
data class UserAttributeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attributeType: String,
    val value: String,
    val updatedAtMillis: Long
)

@Entity(tableName = "sync_records", indices = [Index("source"), Index("startedAtMillis")])
data class SyncRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val status: String,
    val startedAtMillis: Long,
    val completedAtMillis: Long?,
    val detail: String?
)

@Entity(tableName = "imported_files", indices = [Index(value = ["uri"], unique = true)])
data class ImportedFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val mimeType: String?,
    val uri: String,
    val importedAtMillis: Long
)

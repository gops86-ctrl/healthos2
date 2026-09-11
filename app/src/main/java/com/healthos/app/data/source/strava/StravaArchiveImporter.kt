package com.healthos.app.data.source.strava

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.healthos.app.data.local.dao.ActivityDao
import com.healthos.app.data.local.entity.ActivityEntity
import com.healthos.app.domain.model.ActivityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import kotlin.math.abs

class StravaArchiveImporter(
    private val context: Context,
    private val dao: ActivityDao
) {
    data class Result(val imported: Int, val skipped: Int, val error: String? = null)

    data class Progress(val percent: Int, val stage: String, val processed: Int = 0, val total: Int = 0)
    private data class FitRoute(val recordedAtMillis: Long, val route: String)
    private data class FitDefinition(val globalMessageNumber: Int, val fields: List<FitField>, val developerFieldSizes: List<Int>, val littleEndian: Boolean)
    private data class FitField(val number: Int, val size: Int)

    suspend fun importArchive(uri: Uri, onProgress: (Progress) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            onProgress(Progress(0, "Preparing archive…"))
            tempFile = copyUriToCache(uri, onProgress)
            ZipFile(tempFile).use { zip ->
                val entries = zip.entries().asSequence().toList()
                onProgress(Progress(16, "Reading Strava archive…", 0, entries.size))
                val csvEntry = entries.firstOrNull { !it.isDirectory && it.name.substringAfterLast('/').equals("activities.csv", true) }
                    ?: return@withContext Result(0, 0, "activities.csv was not found in this Strava archive.")
                val csv = zip.getInputStream(csvEntry).use { InputStreamReader(it, StandardCharsets.UTF_8).readText() }
                val activityIds = extractActivityIds(csv)
                if (activityIds.isEmpty()) return@withContext Result(0, 0, "No activities were found in the Strava archive.")

                val gpsEntries = entries.filter { entry ->
                    !entry.isDirectory && (entry.name.endsWith(".gpx", true) || entry.name.endsWith(".fit", true) || entry.name.endsWith(".fit.gz", true))
                }
                val gpxRoutes = mutableMapOf<String, String>()
                val fitRoutes = mutableListOf<FitRoute>()
                gpsEntries.forEachIndexed { index, entry ->
                    val filename = entry.name.substringAfterLast('/')
                    when {
                        filename.endsWith(".gpx", true) -> {
                            val id = filename.substringBeforeLast('.')
                            if (id in activityIds) {
                                val text = zip.getInputStream(entry).use { InputStreamReader(it, StandardCharsets.UTF_8).readText() }
                                parseGpxRoute(text)?.let { gpxRoutes[id] = it }
                            }
                        }
                        filename.endsWith(".fit.gz", true) -> {
                            val compressed = zip.getInputStream(entry).use { it.readBytes() }
                            val fitBytes = runCatching { GZIPInputStream(ByteArrayInputStream(compressed)).readBytes() }.getOrNull()
                            parseFitRoute(fitBytes)?.let { fitRoutes += it }
                        }
                        filename.endsWith(".fit", true) -> {
                            zip.getInputStream(entry).use { parseFitRoute(it.readBytes())?.let { route -> fitRoutes += route } }
                        }
                    }
                    if (index == gpsEntries.lastIndex || index % 5 == 0) {
                        onProgress(Progress(20 + ((index + 1) * 50 / gpsEntries.size.coerceAtLeast(1)), "Reading GPS routes…", index + 1, gpsEntries.size))
                    }
                }
                if (gpsEntries.isEmpty()) onProgress(Progress(70, "No GPS route files found"))

                onProgress(Progress(72, "Processing activities…", 0, activityIds.size))
                val activities = parseActivities(csv, gpxRoutes, fitRoutes) { processed, total ->
                    onProgress(Progress(72 + (processed * 23 / total.coerceAtLeast(1)), "Processing activities…", processed, total))
                }
                if (activities.isEmpty()) return@withContext Result(0, 0, "No valid activities were found in the Strava archive.")

                dao.deleteBySourceAndActivityType("STRAVA", ActivityType.STRENGTH.name)
                val enduranceActivities = activities.filter { it.activityType != ActivityType.STRENGTH.name }
                onProgress(Progress(97, "Saving endurance activities…", enduranceActivities.size, enduranceActivities.size))
                if (enduranceActivities.isNotEmpty()) dao.insertAll(enduranceActivities)
                onProgress(Progress(100, "Import complete", enduranceActivities.size, enduranceActivities.size))
                Result(enduranceActivities.size, activities.size - enduranceActivities.size)
            }
        } catch (exception: Exception) {
            Result(0, 0, exception.message ?: "Unable to import the Strava archive.")
        } finally {
            tempFile?.delete()
        }
    }

    private fun copyUriToCache(uri: Uri, onProgress: (Progress) -> Unit): File {
        val file = File.createTempFile("healthos-strava-", ".zip", context.cacheDir)
        val totalBytes = querySize(uri)
        var copiedBytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    copiedBytes += read
                    if (totalBytes != null && totalBytes > 0) {
                        val percent = (copiedBytes * 15L / totalBytes).toInt().coerceIn(0, 15)
                        onProgress(Progress(percent, "Copying archive…"))
                    }
                }
            }
        } ?: throw IllegalStateException("Unable to open the selected archive.")
        onProgress(Progress(15, "Archive ready"))
        return file
    }

    private fun querySize(uri: Uri): Long? {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            if (cursor?.moveToFirst() == true) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
            } else null
        } catch (_: Exception) { null } finally { cursor?.close() }
    }

    private fun extractActivityIds(csv: String): Set<String> {
        val rows = csv.lineSequence().filter { it.isNotBlank() }.map(::parseCsvLine).toList()
        if (rows.size < 2) return emptySet()
        val headers = rows.first().map { it.trim() }
        val index = headers.mapIndexed { position, header -> header.lowercase(Locale.US) to position }.toMap()
        val idIndex = index["activity id"] ?: return emptySet()
        return rows.drop(1).mapNotNull { row -> row.getOrNull(idIndex)?.trim()?.takeIf { it.isNotEmpty() } }.toSet()
    }

    private fun parseActivities(csv: String, routesById: Map<String, String>, fitRoutes: List<FitRoute>, onProgress: (Int, Int) -> Unit): List<ActivityEntity> {
        val rows = csv.lineSequence().filter { it.isNotBlank() }.map(::parseCsvLine).toList()
        if (rows.size < 2) return emptyList()
        val headers = rows.first().map { it.trim() }
        val index = headers.mapIndexed { position, header -> header.lowercase(Locale.US) to position }.toMap()
        fun value(row: List<String>, vararg names: String): String? = names.asSequence().mapNotNull { name -> index[name.lowercase(Locale.US)]?.let { row.getOrNull(it) } }.firstOrNull { it.isNotBlank() }
        val importedAt = System.currentTimeMillis()
        val dataRows = rows.drop(1)
        val total = dataRows.size
        return dataRows.mapIndexedNotNull { rowIndex, row ->
            val rawType = value(row, "Activity Type", "Type")
            val externalId = value(row, "Activity ID", "Activity Id")
            val recordedAt = value(row, "Activity Date", "Activity Date Local", "Start Date")?.let(::parseDateMillis)
            val activity = if (rawType != null && externalId != null && recordedAt != null) {
                val activityType = mapActivityType(rawType)
                val movingTime = parseDurationSeconds(value(row, "Moving Time"))
                val elapsedTime = parseDurationSeconds(value(row, "Elapsed Time"))
                val duration = if (activityType == ActivityType.STRENGTH) elapsedTime ?: movingTime ?: 0L else movingTime ?: elapsedTime ?: 0L
                ActivityEntity(
                    activityType = activityType.name,
                    name = value(row, "Activity Name", "Name") ?: rawType,
                    durationSeconds = duration,
                    elapsedDurationSeconds = elapsedTime,
                    distanceMeters = value(row, "Distance")?.toDoubleOrNull(),
                    averageHeartRate = value(row, "Average Heart Rate")?.toDoubleOrNull()?.toInt(),
                    maxHeartRate = value(row, "Max Heart Rate")?.toDoubleOrNull()?.toInt(),
                    averageSpeedMps = value(row, "Average Speed")?.toDoubleOrNull(),
                    elevationGainMeters = value(row, "Elevation Gain")?.toDoubleOrNull(),
                    calories = value(row, "Calories")?.toDoubleOrNull(),
                    routePoints = routesById[externalId] ?: findNearestFitRoute(fitRoutes, recordedAt),
                    source = "STRAVA",
                    sourceRecordId = externalId,
                    recordedAtMillis = recordedAt,
                    importedAtMillis = importedAt
                )
            } else null
            val processed = rowIndex + 1
            if (processed == total || processed % 10 == 0) onProgress(processed, total)
            activity
        }
    }

    private fun findNearestFitRoute(routes: List<FitRoute>, recordedAtMillis: Long): String? = routes.minByOrNull { abs(it.recordedAtMillis - recordedAtMillis) }
        ?.takeIf { abs(it.recordedAtMillis - recordedAtMillis) <= 12 * 60 * 60 * 1000L }?.route

    private fun parseGpxRoute(gpx: String): String? {
        val pointRegex = Regex("<trkpt\\b[^>]*\\blat=\"([^\"]+)\"[^>]*\\blon=\"([^\"]+)\"[^>]*>")
        val points = pointRegex.findAll(gpx).mapNotNull { match ->
            val lat = match.groupValues[1].toDoubleOrNull()
            val lon = match.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) lat to lon else null
        }.toList()
        return compactRoute(points)
    }

    private fun parseFitRoute(bytes: ByteArray?): FitRoute? {
        if (bytes == null || bytes.size < 14 || String(bytes, 8, 4, StandardCharsets.US_ASCII) != ".FIT") return null
        val headerSize = bytes[0].toInt() and 0xff
        val dataSize = readUInt32(bytes, 4, true).toInt()
        val dataEnd = (headerSize + dataSize).coerceAtMost(bytes.size - 2)
        var position = headerSize
        val definitions = mutableMapOf<Int, FitDefinition>()
        val points = mutableListOf<Pair<Double, Double>>()
        var firstTimestamp: Long? = null
        var lastTimestamp: Long? = null

        while (position < dataEnd) {
            val header = bytes[position++].toInt() and 0xff
            val compressed = header and 0x80 != 0
            val isDefinition = header and 0x40 != 0
            val localType = if (compressed) (header shr 5) and 0x03 else header and 0x0f
            if (isDefinition) {
                if (position + 5 > dataEnd) break
                val littleEndian = (bytes[position + 1].toInt() and 0xff) == 0
                val globalNumber = readUInt16(bytes, position + 2, littleEndian)
                val fieldCount = bytes[position + 4].toInt() and 0xff
                position += 5
                if (position + fieldCount * 3 > dataEnd) break
                val fields = ArrayList<FitField>(fieldCount)
                repeat(fieldCount) {
                    fields += FitField(bytes[position].toInt() and 0xff, bytes[position + 1].toInt() and 0xff)
                    position += 3
                }
                val developerSizes = ArrayList<Int>()
                if (header and 0x20 != 0) {
                    if (position >= dataEnd) break
                    val count = bytes[position++].toInt() and 0xff
                    repeat(count) {
                        if (position + 3 > dataEnd) return null
                        developerSizes += bytes[position + 2].toInt() and 0xff
                        position += 3
                    }
                }
                definitions[localType] = FitDefinition(globalNumber, fields, developerSizes, littleEndian)
                continue
            }
            val definition = definitions[localType] ?: break
            var latitudeRaw: Int? = null
            var longitudeRaw: Int? = null
            var timestampRaw: Long? = null
            for (field in definition.fields) {
                if (compressed && field.number == 253) continue
                if (position + field.size > dataEnd) return null
                if (definition.globalMessageNumber == 20 && field.size == 4) {
                    when (field.number) {
                        0 -> latitudeRaw = readUInt32(bytes, position, definition.littleEndian).toInt()
                        1 -> longitudeRaw = readUInt32(bytes, position, definition.littleEndian).toInt()
                        253 -> timestampRaw = readUInt32(bytes, position, definition.littleEndian)
                    }
                }
                position += field.size
            }
            definition.developerFieldSizes.forEach { size ->
                if (position + size > dataEnd) return null
                position += size
            }
            if (compressed && lastTimestamp != null) {
                val offset = header and 0x1f
                var candidate = (lastTimestamp!! and -32L) + offset
                if (candidate <= lastTimestamp!!) candidate += 32L
                timestampRaw = candidate
            }
            if (timestampRaw != null) {
                lastTimestamp = timestampRaw
                if (firstTimestamp == null) firstTimestamp = timestampRaw
            }
            if (definition.globalMessageNumber == 20 && latitudeRaw != null && longitudeRaw != null) {
                val lat = latitudeRaw.toDouble() * 180.0 / 2147483648.0
                val lon = longitudeRaw.toDouble() * 180.0 / 2147483648.0
                if (lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)) points += lat to lon
            }
        }
        val route = compactRoute(points) ?: return null
        val timestamp = firstTimestamp ?: return null
        return FitRoute(FIT_EPOCH_MILLIS + timestamp * 1000L, route)
    }

    private fun compactRoute(points: List<Pair<Double, Double>>): String? {
        if (points.size < 2) return null
        val maxPoints = 500
        val sampled = if (points.size <= maxPoints) points else {
            val last = points.lastIndex
            (0 until maxPoints).map { i -> points[(i.toLong() * last / (maxPoints - 1)).toInt()] }
        }
        return sampled.joinToString(";") { "${it.first},${it.second}" }
    }

    private fun parseDurationSeconds(raw: String?): Long? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        value.toDoubleOrNull()?.let { if (it >= 0) return it.toLong() }
        val parts = value.split(':')
        if (parts.size !in 2..3) return null
        return try {
            val n = parts.map { it.trim().toLong() }
            if (parts.size == 3) {
                if (n[1] !in 0..59 || n[2] !in 0..59) null else n[0] * 3600L + n[1] * 60L + n[2]
            } else if (n[1] !in 0..59) null else n[0] * 60L + n[1]
        } catch (_: NumberFormatException) { null }
    }

    private fun mapActivityType(raw: String): ActivityType {
        val normalized = raw.lowercase(Locale.US)
        return when {
            normalized.contains("run") -> ActivityType.RUN
            normalized.contains("ride") || normalized.contains("cycling") || normalized.contains("bike") -> ActivityType.RIDE
            normalized.contains("walk") -> ActivityType.WALK
            normalized.contains("hike") -> ActivityType.HIKE
            normalized.contains("swim") -> ActivityType.SWIM
            normalized.contains("weight") || normalized.contains("strength") -> ActivityType.STRENGTH
            normalized.contains("football") || normalized.contains("soccer") -> ActivityType.SOCCER
            normalized.contains("workout") -> ActivityType.WORKOUT
            normalized.contains("rock climb") -> ActivityType.ROCK_CLIMB
            normalized.contains("canoe") -> ActivityType.CANOE
            else -> ActivityType.OTHER
        }
    }

    private fun parseDateMillis(raw: String): Long? {
        val patterns = listOf("MMM d, yyyy, h:mm:ss a", "MMM d, yyyy, h:mm a", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for (pattern in patterns) try {
            val parser = SimpleDateFormat(pattern, Locale.US)
            parser.timeZone = TimeZone.getDefault()
            parser.parse(raw)?.let { return it.time }
        } catch (_: Exception) { }
        return null
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var position = 0
        while (position < line.length) {
            when (val character = line[position]) {
                '"' -> if (quoted && position + 1 < line.length && line[position + 1] == '"') { current.append('"'); position++ } else quoted = !quoted
                ',' -> if (quoted) current.append(character) else { result += current.toString(); current.setLength(0) }
                else -> current.append(character)
            }
            position++
        }
        result += current.toString()
        return result
    }

    private fun readUInt16(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int {
        val a = bytes[offset].toInt() and 0xff
        val b = bytes[offset + 1].toInt() and 0xff
        return if (littleEndian) a or (b shl 8) else (a shl 8) or b
    }

    private fun readUInt32(bytes: ByteArray, offset: Int, littleEndian: Boolean): Long {
        val a = bytes[offset].toLong() and 0xff
        val b = bytes[offset + 1].toLong() and 0xff
        val c = bytes[offset + 2].toLong() and 0xff
        val d = bytes[offset + 3].toLong() and 0xff
        return if (littleEndian) a or (b shl 8) or (c shl 16) or (d shl 24) else (a shl 24) or (b shl 16) or (c shl 8) or d
    }

    companion object { private const val FIT_EPOCH_MILLIS = 631065600000L }
}

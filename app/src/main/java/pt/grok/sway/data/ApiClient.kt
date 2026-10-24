package pt.grok.sway.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import pt.grok.sway.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

class ApiException(val status: Int) : Exception("HTTP $status")

/**
 * Talks to the public Sway API (`/pub/...`). No user auth: prescriptions are fetched by code,
 * and diary entries and exercise runs are posted with the client submission token.
 */
class ApiClient(private val baseUrl: String = BuildConfig.API_BASE_URL) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun prescription(code: String): Prescription {
        val body = request("GET", "/pub/prescriptions/${encode(code)}")
        return json.decodeFromString(Prescription.serializer(), body)
    }

    fun fileUrl(document: Prescription.Document) = "$baseUrl/pub/prescriptions/files/${encode(document.fileId)}"

    suspend fun submit(entry: DiaryEntry, token: String) {
        request("POST", "/pub/bladder-bowel-diary", diaryPayload(entry), token)
    }

    suspend fun submit(run: ExerciseRun, token: String) {
        val payload = buildJsonObject {
            put("startedAt", iso(run.startedAt))
            put("endedAt", iso(run.endedAt))
            put("completedSteps", run.completedSteps)
            put("totalSteps", run.totalSteps)
            put("terminatedEarly", run.terminatedEarly)
            run.prescriptionId?.let { put("prescriptionId", it) }
        }
        request("POST", "/pub/exercise-runs", payload, token)
    }

    private suspend fun request(method: String, path: String, body: JsonObject? = null, token: String? = null): String =
        withContext(Dispatchers.IO) {
            val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val status = connection.responseCode
                if (status !in 200..299) throw ApiException(status)
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        private fun iso(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).toString()

        /** Same wire format as the iOS app, so the backend and web dashboard read both alike. */
        fun diaryPayload(entry: DiaryEntry): JsonObject = buildJsonObject {
            val type = when (entry.details) {
                DiaryEntry.Details.WakeUp -> "wake"
                DiaryEntry.Details.Sleep -> "sleep"
                is DiaryEntry.Details.Drink -> "drink"
                is DiaryEntry.Details.Urination -> "urinate"
                is DiaryEntry.Details.Bowel -> "defecate"
            }
            put("entryType", type)
            put("entryDatetime", iso(entry.time))
            putJsonObject("data") {
                put("notes", entry.notes)
                when (val d = entry.details) {
                    DiaryEntry.Details.WakeUp, DiaryEntry.Details.Sleep -> Unit
                    is DiaryEntry.Details.Drink -> {
                        put("drinkType", d.type.apiValue)
                        put("volume", d.volumeMl)
                    }
                    is DiaryEntry.Details.Urination -> {
                        put("volume", d.volumeMl)
                        put("urgency", d.urgency)
                        put("leaked", d.leaked)
                    }
                    is DiaryEntry.Details.Bowel -> {
                        put("urgency", d.urgency)
                        put("accident", d.accident)
                        put("accidentAmount", d.accidentAmount)
                        put("timeTaken", d.minutesTaken)
                        put("stoolType", d.stoolType)
                        put("effortRequired", d.effort)
                        put("feltEmpty", d.feltEmpty)
                    }
                }
            }
        }
    }
}

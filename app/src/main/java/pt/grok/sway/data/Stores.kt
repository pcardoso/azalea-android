package pt.grok.sway.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The signed-in client. A "session" is just a prescription code that resolved successfully,
 * plus the submission token the backend hands back for posting diary entries and runs.
 */
class SessionStore(context: Context, private val api: ApiClient) {
    data class State(
        val code: String? = null,
        val name: String? = null,
        val submissionToken: String? = null,
        val prescription: Prescription? = null,
    ) {
        val isSignedIn: Boolean get() = code != null
    }

    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        State(
            code = prefs.getString("code", null),
            name = prefs.getString("name", null),
            submissionToken = prefs.getString("token", null),
        )
    )
    val state: StateFlow<State> = _state.asStateFlow()

    /** Validates the code against the API and stores the session on success. */
    suspend fun signIn(rawCode: String) {
        val code = rawCode.trim().uppercase()
        val prescription = api.prescription(code)
        val next = State(code, prescription.client.displayName, prescription.clientSubmissionToken, prescription)
        _state.value = next
        persist(next)
    }

    /** Reloads the prescription for the current session (pull to refresh, app launch). */
    suspend fun refreshPrescription() {
        val code = _state.value.code ?: return
        val prescription = api.prescription(code)
        _state.update {
            it.copy(
                name = prescription.client.displayName,
                prescription = prescription,
                submissionToken = prescription.clientSubmissionToken ?: it.submissionToken,
            )
        }
        persist(_state.value)
    }

    fun signOut() {
        _state.value = State()
        prefs.edit().clear().apply()
    }

    private fun persist(state: State) {
        prefs.edit()
            .putString("code", state.code)
            .putString("name", state.name)
            .putString("token", state.submissionToken)
            .apply()
    }
}

/** A list persisted as JSON in app storage, saved first and synced to the backend afterwards. */
abstract class SyncedListStore<T>(
    context: Context,
    fileName: String,
    private val serializer: KSerializer<T>,
) {
    private val file = File(context.filesDir, fileName)
    // "type" is taken by DiaryEntry.Details.Drink, so tag the sealed classes with "kind".
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }
    private val syncLock = Mutex()

    protected val _items = MutableStateFlow(load())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    protected abstract fun idOf(item: T): String
    protected abstract fun isSynced(item: T): Boolean
    protected abstract fun markSynced(item: T): T
    protected abstract suspend fun upload(item: T, token: String)

    /** Saves the item locally, then tries to send it. Returns false if it could not be synced yet. */
    suspend fun add(item: T, token: String?): Boolean {
        _items.update { it + item }
        save()
        return token != null && sync(idOf(item), token)
    }

    fun delete(item: T) {
        _items.update { list -> list.filterNot { idOf(it) == idOf(item) } }
        save()
    }

    /** Retries everything that hasn't reached the server yet. */
    suspend fun syncPending(token: String?) {
        if (token == null || syncLock.isLocked) return
        syncLock.withLock {
            _isSyncing.value = true
            try {
                _items.value.filterNot(::isSynced).forEach { sync(idOf(it), token) }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun sync(id: String, token: String): Boolean {
        val item = _items.value.firstOrNull { idOf(it) == id } ?: return false
        return try {
            upload(item, token)
            _items.update { list -> list.map { if (idOf(it) == id) markSynced(it) else it } }
            save()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun load(): List<T> = try {
        if (file.exists()) json.decodeFromString(ListSerializer(serializer), file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun save() {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(ListSerializer(serializer), _items.value))
        tmp.renameTo(file)
    }
}

class DiaryStore(context: Context, private val api: ApiClient) :
    SyncedListStore<DiaryEntry>(context, "diary.json", DiaryEntry.serializer()) {
    override fun idOf(item: DiaryEntry) = item.id
    override fun isSynced(item: DiaryEntry) = item.synced
    override fun markSynced(item: DiaryEntry) = item.copy(synced = true)
    override suspend fun upload(item: DiaryEntry, token: String) = api.submit(item, token)
}

class ExerciseRunStore(context: Context, private val api: ApiClient) :
    SyncedListStore<ExerciseRun>(context, "exercise-runs.json", ExerciseRun.serializer()) {
    override fun idOf(item: ExerciseRun) = item.id
    override fun isSynced(item: ExerciseRun) = item.synced
    override fun markSynced(item: ExerciseRun) = item.copy(synced = true)
    override suspend fun upload(item: ExerciseRun, token: String) = api.submit(item, token)
}

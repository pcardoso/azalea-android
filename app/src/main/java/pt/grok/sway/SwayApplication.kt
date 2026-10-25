package pt.grok.sway

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import pt.grok.sway.data.ApiClient
import pt.grok.sway.data.DiaryStore
import pt.grok.sway.data.ExerciseRunStore
import pt.grok.sway.data.ReminderSettings
import pt.grok.sway.data.SessionStore
import pt.grok.sway.data.UpdateChecker

/** Holds the app-wide stores; screens reach them through [LocalApp]. */
class SwayApplication : Application() {
    /** For work that must outlive a screen, like sending a run after the session closes. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var api: ApiClient
    lateinit var session: SessionStore
    lateinit var diary: DiaryStore
    lateinit var exerciseRuns: ExerciseRunStore
    lateinit var reminders: ReminderSettings
    lateinit var updates: UpdateChecker

    override fun onCreate() {
        super.onCreate()
        api = ApiClient()
        session = SessionStore(this, api)
        diary = DiaryStore(this, api)
        exerciseRuns = ExerciseRunStore(this, api)
        reminders = ReminderSettings(this)
        updates = UpdateChecker()
        ReminderSettings.createChannel(this)
    }
}

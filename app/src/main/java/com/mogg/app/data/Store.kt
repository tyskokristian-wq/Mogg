package com.mogg.app.data

import android.content.Context
import com.mogg.app.widget.MoggWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** Всё хранится только на телефоне, в одном JSON-файле. Без облака. */
object Store {
    private lateinit var file: File
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val lock = Any()

    fun init(context: Context) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        file = File(appContext.filesDir, "state.json")
        _state.value = load()
    }

    fun load(context: Context): AppState {
        init(context)
        return _state.value
    }

    private fun load(): AppState = try {
        if (file.exists()) JsonConfig.json.decodeFromString(AppState.serializer(), file.readText()) else AppState()
    } catch (e: Exception) {
        AppState()
    }

    fun today(): String = LocalDate.now().toString()

    fun day(date: String = today()): DayLog = _state.value.days[date] ?: DayLog(date)

    fun updateProfile(block: (Profile) -> Profile) = mutate { it.copy(profile = block(it.profile)) }

    fun updateDay(date: String = today(), block: (DayLog) -> DayLog) = mutate { s ->
        val d = s.days[date] ?: DayLog(date)
        s.copy(days = s.days + (date to block(d)))
    }

    private fun mutate(block: (AppState) -> AppState) {
        val newState: AppState
        synchronized(lock) {
            newState = block(_state.value)
            _state.value = newState
        }
        scope.launch {
            synchronized(lock) {
                val tmp = File(file.parentFile, "state.json.tmp")
                tmp.writeText(JsonConfig.json.encodeToString(AppState.serializer(), newState))
                tmp.renameTo(file)
            }
            MoggWidget.updateAll(appContext)
        }
    }

    /** Серия дней подряд, когда сделаны и утро, и вечер. */
    fun streak(state: AppState = _state.value): Int {
        var d = LocalDate.now()
        var count = 0
        val todayLog = state.days[d.toString()]
        if (todayLog == null || !(todayLog.morningDone && todayLog.eveningDone)) d = d.minusDays(1)
        while (true) {
            val log = state.days[d.toString()] ?: break
            if (log.morningDone && log.eveningDone) count++ else break
            d = d.minusDays(1)
        }
        return count
    }
}

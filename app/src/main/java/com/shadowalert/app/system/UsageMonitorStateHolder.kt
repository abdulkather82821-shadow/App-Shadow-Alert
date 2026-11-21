package com.shadowalert.app.system

import com.shadowalert.app.domain.model.InterventionTriggerType
import com.shadowalert.app.domain.model.MonitoringState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class PauseTriggerEvent(
    val sessionId: Long,
    val packageName: String,
    val appName: String,
    val intendedMinutes: Int,
    val triggerType: InterventionTriggerType = InterventionTriggerType.INTENTION_COMPLETE
)

class UsageMonitorStateHolder {
    private val _monitoringState = MutableStateFlow(MonitoringState())
    val monitoringState: StateFlow<MonitoringState> = _monitoringState.asStateFlow()

    private val _pauseEvents = MutableSharedFlow<PauseTriggerEvent>(extraBufferCapacity = 4)
    val pauseEvents: SharedFlow<PauseTriggerEvent> = _pauseEvents.asSharedFlow()

    fun update(state: MonitoringState) {
        _monitoringState.value = state
    }

    fun emitPauseEvent(event: PauseTriggerEvent) {
        _pauseEvents.tryEmit(event)
    }
}

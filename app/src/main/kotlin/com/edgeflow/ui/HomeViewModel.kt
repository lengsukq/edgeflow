package com.edgeflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.edgeflow.data.ConfigRepository
import com.edgeflow.data.Guidance
import com.edgeflow.data.ModuleStatus
import com.edgeflow.data.ModuleStatusProbe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Backs the home screen.
 *
 * The status probe touches the class loader and the asset manager, so it runs
 * off the composition and its result is published as state. Probing on every
 * recomposition would be both wasteful and jittery.
 */
class HomeViewModel(
    private val statusProbe: ModuleStatusProbe,
    private val configRepository: ConfigRepository,
) : ViewModel() {

    private val _status = MutableStateFlow<ModuleStatus?>(null)
    private var refreshJob: Job? = null

    /** Null until the first probe completes, so the UI can show a placeholder. */
    val status: StateFlow<ModuleStatus?> = _status.asStateFlow()

    /** Live configuration, for the summary counts on the home screen. */
    val config = configRepository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), configRepository.current)

    init {
        refresh()
    }

    /** Re-runs the probe; used by the "Recheck" button. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _status.value = statusProbe.probe()
            // Framework binder and system broadcasts are asynchronous; a
            // single 850 ms recheck can miss an otherwise valid response.
            repeat(8) {
                if (_status.value?.frameworkDetected == true) return@launch
                delay(400)
                _status.value = statusProbe.probe()
            }
        }
    }

    /** Whether the status card should offer remediation steps. */
    fun needsAttention(status: ModuleStatus): Boolean = status.guidance != Guidance.READY

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Factory so the ViewModel receives its dependencies explicitly. */
        fun factory(
            statusProbe: ModuleStatusProbe,
            configRepository: ConfigRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(statusProbe, configRepository) as T
        }
    }
}

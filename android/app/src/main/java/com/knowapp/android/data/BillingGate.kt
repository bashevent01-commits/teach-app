package com.knowapp.android.data

import android.content.Context
import com.knowapp.android.data.model.BillingStatusOut
import com.knowapp.android.data.repository.BillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

// The server answers 402 when a workspace is closed for an unpaid fee; this tells the app straight away
object BillingSignal {
    val closed = MutableStateFlow(0L)
    fun fire() { closed.value = System.currentTimeMillis() }
}

// True while this workspace is on the free plan; screens use it to show the upgrade banner
object PlanState {
    val free = MutableStateFlow(false)
    val upgradeRequest = MutableStateFlow(0L)
    fun requestUpgrade() { upgradeRequest.value = System.currentTimeMillis() }
}

data class GateState(
    val status: BillingStatusOut? = null,
    val payOpen: Boolean = false,
    val preview: Boolean = false,
    val previewAgent: Boolean = false,
    val dismissedDay: String? = null,
)

// Holds the fee reminder state: when to remind, when the app closes, and the payment steps
class BillingGate(context: Context, private val repository: BillingRepository, private val sessionStore: com.knowapp.android.data.SessionStore) {
    private val prefs = context.applicationContext.getSharedPreferences("know_billing", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(GateState())
    val state: StateFlow<GateState> = _state

    private fun today(): String = LocalDate.now(ZoneId.of("Africa/Nairobi")).toString()

    suspend fun refresh(userId: Int) {
        repository.status().onSuccess { s ->
            PlanState.free.value = s.tier == "free" && !s.premium
            sessionStore.setWorkspace(s.individual, s.institutionType)
            _state.update { it.copy(status = s, dismissedDay = prefs.getString("later_$userId", null)) }
        }
    }

    fun reset() {
        PlanState.free.value = false
        _state.value = GateState()
    }

    // "Remind me later" hides the reminder until 4pm tomorrow
    fun remindTomorrow(userId: Int) {
        prefs.edit().putString("later_$userId", today()).apply()
        _state.update { it.copy(dismissedDay = today(), payOpen = false) }
    }

    fun openPay() { _state.update { it.copy(payOpen = true) } }
    fun closePay() { _state.update { it.copy(payOpen = false) } }
    fun startPreview() { _state.update { it.copy(preview = true) } }
    fun endPreview() { _state.update { it.copy(preview = false, previewAgent = false) } }
    fun setPreviewAgent(on: Boolean) { _state.update { it.copy(previewAgent = on) } }

    suspend fun sendProof(message: String, userId: Int): Result<String> =
        repository.sendProof(message).onSuccess { refresh(userId) }
}

package com.personalmentor.app.domain.agent

import com.personalmentor.app.domain.model.ApprovalMode
import com.personalmentor.app.domain.repository.AgentSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** [SENSITIVE] actions are asked in "sensitive" and "all" modes; [ROUTINE] ones only in "all" mode. */
enum class ApprovalLevel { ROUTINE, SENSITIVE }

/** What the tool executors depend on, so tests can replace the dialog with a fake. */
interface ActionApprover {
    /** True when the action may go ahead (the user said yes, or the current mode does not ask). */
    suspend fun confirm(title: String, detail: String, level: ApprovalLevel): Boolean
}

/**
 * Put in the coroutine context of an unattended scheduled run. [skipConfirmations] turns every question off;
 * otherwise sensitive actions are always asked, whatever the global approval mode, and [onWaiting] is called
 * first so the app can notify the user that a question is waiting.
 */
class RunApprovalPolicy(
    val skipConfirmations: Boolean,
    val onWaiting: suspend () -> Unit = {},
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RunApprovalPolicy>
}

data class ApprovalRequest(val id: Long, val title: String, val detail: String)

/**
 * Pauses the agent until the user approves or declines an action. The app shows [pending] as a dialog.
 * No answer within [TIMEOUT_MS] counts as "declined", so a forgotten request never runs later.
 */
@Singleton
class ApprovalGate @Inject constructor(
    private val settings: AgentSettingsRepository,
) : ActionApprover {

    private val _pending = MutableStateFlow<ApprovalRequest?>(null)
    val pending: StateFlow<ApprovalRequest?> = _pending.asStateFlow()

    private val lock = Mutex() // one question at a time
    @Volatile private var answer: CompletableDeferred<Boolean>? = null
    private var nextId = 1L

    override suspend fun confirm(title: String, detail: String, level: ApprovalLevel): Boolean {
        val policy = currentCoroutineContext()[RunApprovalPolicy]
        val mode = settings.current().approvalMode
        val needed = when {
            policy?.skipConfirmations == true -> false
            policy != null -> level == ApprovalLevel.SENSITIVE || mode == ApprovalMode.ALL
            else -> when (mode) {
                ApprovalMode.NONE -> false
                ApprovalMode.SENSITIVE -> level == ApprovalLevel.SENSITIVE
                ApprovalMode.ALL -> true
            }
        }
        if (!needed) return true
        policy?.onWaiting?.invoke()
        return lock.withLock {
            val deferred = CompletableDeferred<Boolean>()
            answer = deferred
            _pending.value = ApprovalRequest(nextId++, title, detail)
            try {
                withTimeoutOrNull(TIMEOUT_MS) { deferred.await() } ?: false
            } finally {
                _pending.value = null
                answer = null
            }
        }
    }

    /** Called by the dialog buttons. */
    fun resolve(approved: Boolean) {
        answer?.complete(approved)
    }

    companion object {
        const val TIMEOUT_MS = 5 * 60 * 1000L
    }
}

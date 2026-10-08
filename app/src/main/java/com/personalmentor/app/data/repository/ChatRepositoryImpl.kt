package com.personalmentor.app.data.repository

import com.personalmentor.app.data.local.ChatMessageDao
import com.personalmentor.app.data.local.toDomain
import com.personalmentor.app.data.local.toEntity
import com.personalmentor.app.domain.assistant.AssistantResponder
import com.personalmentor.app.domain.model.AssistantMode
import com.personalmentor.app.domain.model.ChatMessage
import com.personalmentor.app.domain.model.Sender
import com.personalmentor.app.domain.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val dao: ChatMessageDao,
    private val responder: AssistantResponder,
) : ChatRepository {

    override fun observeMessages(mode: AssistantMode): Flow<List<ChatMessage>> =
        dao.observeByMode(mode.name).map { list -> list.map { it.toDomain() } }

    override suspend fun sendMessage(text: String, mode: AssistantMode): Result<Unit> {
        var replyId: Long? = null
        val buffer = StringBuilder()
        return try {
            dao.insert(ChatMessage(text = text, sender = Sender.USER, mode = mode).toEntity())
            val history = dao.getRecent(mode.name, HISTORY_WINDOW).map { it.toDomain() }.asReversed()

            // Empty placeholder: the UI shows a typing indicator until the first token arrives.
            val id = dao.insert(ChatMessage(text = "", sender = Sender.ASSISTANT, mode = mode).toEntity())
            replyId = id

            var lastWrite = 0L
            responder.respond(mode, history).collect { delta ->
                buffer.append(delta)
                val now = System.currentTimeMillis()
                if (now - lastWrite >= WRITE_INTERVAL_MS) { // throttle DB writes while streaming
                    dao.updateText(id, buffer.toString())
                    lastWrite = now
                }
            }
            persistReply(id, buffer)
            Result.success(Unit)
        } catch (e: CancellationException) {
            replyId?.let { withContext(NonCancellable) { persistReply(it, buffer) } }
            throw e
        } catch (e: Exception) {
            replyId?.let { persistReply(it, buffer) } // keep any partial answer
            Result.failure(e)
        }
    }

    override suspend fun clear(mode: AssistantMode) = dao.clear(mode.name)

    /** Saves the final text, or removes the placeholder if nothing was produced. */
    private suspend fun persistReply(id: Long, buffer: StringBuilder) {
        if (buffer.isBlank()) dao.deleteById(id) else dao.updateText(id, buffer.toString())
    }

    private companion object {
        const val HISTORY_WINDOW = 20
        const val WRITE_INTERVAL_MS = 60L
    }
}

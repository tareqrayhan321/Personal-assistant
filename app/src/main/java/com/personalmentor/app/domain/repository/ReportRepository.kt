package com.personalmentor.app.domain.repository

import com.personalmentor.app.domain.model.ChatMessage
import com.personalmentor.app.domain.model.ReportReason

interface ReportRepository {
    /** Sends an AI response the user flagged to the developer (Google Play AI-content policy). */
    suspend fun report(message: ChatMessage, reason: ReportReason, note: String): Result<Unit>
}

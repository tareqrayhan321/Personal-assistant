package com.personalmentor.app.domain.computer

import com.personalmentor.app.domain.model.CloudComputer
import kotlinx.serialization.json.JsonObject

class ComputerException(message: String) : Exception(message)

/** Client for the `cloud-computer/` server. Throws [ComputerException] with a readable message on any failure. */
interface ComputerApi {
    /** GET when [body] is null, POST otherwise. Returns the JSON object of a successful (`ok: true`) answer. */
    suspend fun call(
        computer: CloudComputer,
        endpoint: String,
        body: JsonObject? = null,
        timeoutSeconds: Int = 30,
    ): JsonObject
}

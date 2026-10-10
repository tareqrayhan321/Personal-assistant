package com.personalmentor.app.domain.model

/** A machine the user owns that runs the `cloud-computer/` server; scheduled tasks can work on it. */
data class CloudComputer(
    val id: Long = 0,
    val name: String,
    /** https URL of the server (plain http only for localhost / the emulator host). */
    val url: String,
    val token: String,
    val createdAt: Long = System.currentTimeMillis(),
)

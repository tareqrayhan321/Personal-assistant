package com.personalmentor.app.domain.model

enum class Repeat {
    NONE, DAILY, WEEKLY;

    val label: String
        get() = when (this) {
            NONE -> "Once"
            DAILY -> "Daily"
            WEEKLY -> "Weekly"
        }

    companion object {
        /** Case-insensitive; null for an unknown value. */
        fun parseOrNull(raw: String): Repeat? = entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }

        fun fromName(name: String?): Repeat = name?.let(::parseOrNull) ?: NONE
    }
}

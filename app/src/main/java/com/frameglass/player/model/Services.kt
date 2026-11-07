package com.frameglass.player.model

/** One metadata service on the keys page. Its switch is separate from its key and never removes it. */
internal data class ServiceSwitch(val hasKey: Boolean, val enabled: Boolean) {
    /** A service with no key is off, whatever its stored switch says. */
    val on: Boolean get() = hasKey && enabled

    /** Only a saved key can be switched, so the switch waits for one. */
    val switchable: Boolean get() = hasKey
}

/**
 * Who identifies new videos and answers Fix match right now. NONE means both services are off or have no key, and
 * OFFLINE means Online features is off, which stops both whatever their switches say.
 */
internal enum class Identifier { TMDB, IMDB, NONE, OFFLINE }

/** The line under TMDB that says who identifies new videos while TMDB is not on. */
internal enum class FallbackNote { THROUGH_IMDB, NOTHING, NONE }

/** The keys page as :data reads it: tmdbUsable and omdbUsable in SettingsStore follow the same rule. */
internal data class ServiceState(val online: Boolean, val tmdb: ServiceSwitch, val imdb: ServiceSwitch) {
    val identifier: Identifier
        get() = when {
            !online -> Identifier.OFFLINE
            tmdb.on -> Identifier.TMDB
            imdb.on -> Identifier.IMDB
            else -> Identifier.NONE
        }

    // With no TMDB key and no IMDb, nothing was ever switched off, and the key line already says what to do.
    val tmdbFallback: FallbackNote
        get() = when {
            !online || tmdb.on -> FallbackNote.NONE
            imdb.on -> FallbackNote.THROUGH_IMDB
            tmdb.hasKey -> FallbackNote.NOTHING
            else -> FallbackNote.NONE
        }
}

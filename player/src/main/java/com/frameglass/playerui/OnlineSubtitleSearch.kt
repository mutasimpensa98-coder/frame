package com.frameglass.playerui

import androidx.annotation.StringRes
import com.frameglass.data.api.SubtitleFailure
import com.frameglass.data.api.SubtitleResult
import com.frameglass.net.NetResult

/** F-S9 as the sheet shows it. Kept per item, so coming back to the sheet costs no second search. */
internal data class OnlineSearch(
    val searching: Boolean = false,
    /** Null until a search has answered. */
    val results: List<SubtitleResult>? = null,
    val failure: OnlineFailure? = null,
    /** The row being downloaded. */
    val loading: SubtitleResult? = null,
)

/** What went wrong, in words for the sheet. Nothing here ever leaves it. */
internal enum class OnlineFailure(@StringRes val message: Int) {
    OFFLINE(R.string.player_online_off),
    NO_KEY(R.string.player_online_no_key),
    NETWORK(R.string.player_online_network),
    LIMIT(R.string.player_online_limit),
    NOT_IN_PACK(R.string.player_online_missing),
    UNREADABLE(R.string.player_online_bad),
    ;

    companion object {
        fun of(error: Throwable?): OnlineFailure = when ((error as? SubtitleFailure)?.reason) {
            NetResult.Reason.OFFLINE_SETTING -> OFFLINE
            NetResult.Reason.NO_KEY -> NO_KEY
            NetResult.Reason.RATE_LIMIT -> LIMIT
            NetResult.Reason.NOT_FOUND -> NOT_IN_PACK
            NetResult.Reason.BAD_RESPONSE -> UNREADABLE
            NetResult.Reason.NETWORK, null -> NETWORK
        }
    }
}

internal object OnlineOrder {
    /** The client ranks a hash match above language, and F-S9 wants Arabic first, so Arabic moves up as a block. */
    fun arabicFirst(results: List<SubtitleResult>): List<SubtitleResult> =
        results.sortedBy { !TrackPicker.isArabic(it.language) }
}

/** Search found nothing, which is an answer and not a failure. */
internal fun Throwable.meansNothingFound(): Boolean = (this as? SubtitleFailure)?.reason == NetResult.Reason.NOT_FOUND

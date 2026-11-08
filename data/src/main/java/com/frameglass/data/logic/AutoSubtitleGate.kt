package com.frameglass.data.logic

import com.frameglass.data.api.MatchState
import com.frameglass.data.db.NameKinds

/**
 * Which videos the automatic subtitle search may run for (waiver of 2026-11-08). The search sends a title, so that
 * title must be the parsed or matched name of a known title, never a file name: an Open with item, a file with no
 * title yet, an unmatched title or a clip-like name stays with the manual search only.
 */
internal object AutoSubtitleGate {
    fun allows(fileRowId: Long?, rowNameKind: String?, titleId: Long?, matchState: MatchState?): Boolean =
        fileRowId != null &&
            rowNameKind != null &&
            rowNameKind != NameKinds.CLIP &&
            titleId != null &&
            (matchState == MatchState.AUTO || matchState == MatchState.MANUAL)
}

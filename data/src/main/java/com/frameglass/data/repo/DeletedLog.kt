package com.frameglass.data.repo

import com.frameglass.data.api.DeletedFiles
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * What confirmed deletes took off storage in this run of the app. The player closes on [events] when its video is
 * among them. A position save that was already on its way when the file went is refused by [holds], so a deleted
 * video never becomes the last thing played. No Android in it, so a JVM test covers it.
 */
internal class DeletedLog {
    private val rows: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val flow = MutableSharedFlow<DeletedFiles>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val events: Flow<DeletedFiles> get() = flow

    fun add(deleted: DeletedFiles) {
        if (deleted.fileRowIds.isEmpty() && deleted.paths.isEmpty()) return
        rows += deleted.fileRowIds
        flow.tryEmit(deleted)
    }

    fun holds(fileRowId: Long?): Boolean = fileRowId != null && fileRowId in rows

    /** The file opened again, as one restored from the phone's trash does, so its saves count again. */
    fun forget(fileRowId: Long) {
        rows -= fileRowId
    }
}

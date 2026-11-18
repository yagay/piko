package dev.piko

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.piko.data.repository.FileCategory
import dev.piko.data.repository.fileCategory
import dev.piko.shared.media.player.PlaylistEntry
import dev.piko.shared.media.player.buildRawPlaylist
import dev.piko.shared.data.PikoClientManager
import dev.piko.shared.data.PreviewTempFolder
import dev.piko.shared.log.PikoLog
import io.github.nihildigit.pikpak.ResolvedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Retained magnet preview files are independent of Piko-Temp's short-lived preview
 * leases. An explicit file-ID + account ledger ensures expiry never deletes arbitrary
 * cloud files; playback IDs remain protected until the player is dismissed.
 */
class MagnetPlaybackStore(
    context: Context,
    private val clients: PikoClientManager,
    private val preview: PreviewTempFolder,
    private val scope: CoroutineScope,
    private val retentionDays: suspend () -> Int,
) {
    private val prefs = context.getSharedPreferences("magnet_playback_retention", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val protected = mutableSetOf<String>()
    private val prepared = mutableMapOf<String, String>()
    private var generation = 0L

    var playlist by mutableStateOf<List<PlaylistEntry>>(emptyList())
        private set

    var currentFileId by mutableStateOf<String?>(null)
        private set

    private data class Record(val account: String, val id: String, val at: Long)
    private fun read(): List<Record> = runCatching {
        val list = JSONArray(prefs.getString("entries", "[]"))
        (0 until list.length()).mapNotNull { index ->
            val data = list.optJSONObject(index) ?: return@mapNotNull null
            val account = data.optString("a")
            val id = data.optString("i")
            val at = data.optLong("t")
            if (account.isEmpty() || id.isEmpty() || at <= 0L) null else Record(account, id, at)
        }
    }.getOrDefault(emptyList())

    private fun write(records: List<Record>) {
        val list = JSONArray()
        records.forEach { list.put(JSONObject().put("a", it.account).put("i", it.id).put("t", it.at)) }
        prefs.edit().putString("entries", list.toString()).commit()
    }

    private suspend fun retain(account: String, file: ResolvedFile): Result<String> {
        // Reuse this session's file, not any other account's previous session.
        val key = "$account|${file.path}|${file.gcid}"
        prepared[key]?.let { return Result.success(it) }
        val saved = preview.retainForPlayback(file)
        saved.onSuccess { id ->
            lock.withLock {
                prepared[key] = id
                val before = read()
                write(before + Record(account, id, System.currentTimeMillis()))
            }
        }
        return saved
    }

    /**
     * Open the chosen indexed video immediately. Prepare its siblings separately
     * without blocking the first frame; all subsequent IDs use the existing player.
     */
    suspend fun open(chosen: ResolvedFile, videos: List<ResolvedFile>): Result<String> {
        val account = clients.currentClient.value?.account
            ?: return Result.failure(IllegalStateException("Not logged in"))
        if (chosen.gcid == null || chosen.name.fileCategory() != FileCategory.VIDEO) {
            return Result.failure(IllegalArgumentException("Video is not available for instant playback"))
        }
        val selected = retain(account, chosen).getOrElse { return Result.failure(it) }
        lock.withLock {
            generation++
            protected.clear()
            protected += selected
            currentFileId = selected
            playlist = listOf(PlaylistEntry(selected, chosen.name, chosen.name, size = chosen.size))
        }
        val revision = generation
        scope.launch(Dispatchers.IO) {
            val sources = videos.filter { it.gcid != null && it.name.fileCategory() == FileCategory.VIDEO }
                .distinctBy { it.path }.sortedBy { it.name }
            val ids = mutableListOf(PlaylistEntry(selected, chosen.name, chosen.name, size = chosen.size))
            for (file in sources) {
                if (revision != generation || clients.currentClient.value?.account != account) break
                if (file.path == chosen.path) continue
                val id = retain(account, file).getOrNull() ?: continue
                ids += PlaylistEntry(id, file.name, file.name, size = file.size)
                lock.withLock {
                    if (revision == generation) {
                        protected += id
                        playlist = buildRawPlaylist(ids.toList())
                    }
                }
            }
        }
        return Result.success(selected)
    }

    fun closePlayer() {
        generation++
        protected.clear()
        prepared.clear()
        currentFileId = null
        playlist = emptyList()
        scope.launch { cleanupExpired() }
    }

    /** Called on startup/account switch and periodically while process is alive. */
    suspend fun cleanupExpired() {
        val account = clients.currentClient.value?.account ?: return
        val days = retentionDays().coerceIn(0, 365)
        val cutoff = System.currentTimeMillis() - days.toLong() * 86_400_000L
        lock.withLock {
            val records = read().toMutableList()
            var changed = false
            for (record in records.toList()) {
                if (record.account != account || record.at > cutoff || record.id in protected) continue
                if (preview.deleteRetainedPlayback(account, record.id)) {
                    records.remove(record)
                    changed = true
                }
            }
            if (changed) write(records)
        }
    }
}

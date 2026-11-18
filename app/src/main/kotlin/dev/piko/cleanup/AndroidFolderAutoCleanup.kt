package dev.piko.cleanup

import android.content.Context
import android.util.Base64
import dev.piko.data.repository.DriveRepository
import dev.piko.shared.data.isPikoInternalFolder
import dev.piko.shared.log.PikoLog
import dev.piko.ui.FolderAutoCleanup
import dev.piko.ui.FolderCleanupRule
import io.github.nihildigit.pikpak.FileStat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Deletes nothing permanently: only uses the existing PikPak moveToTrash endpoint.
 * No local-mounts, folders, unknown-size entries, nested children, or internal metadata.
 * Runs on opening a folder; not a misleading "always-running" Android background timer.
 */
class AndroidFolderAutoCleanup(
    context: Context,
    private val repository: DriveRepository,
) : FolderAutoCleanup {
    private val prefs = context.getSharedPreferences("piko_folder_auto_cleanup_v1", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val lastChecked = mutableMapOf<String, Long>()

    private fun key(account: String, folderId: String): String {
        val raw = "$account\u0000$folderId".toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(raw, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    override fun getRule(account: String, folderId: String): FolderCleanupRule {
        if (folderId.isBlank()) return FolderCleanupRule()
        val stored = prefs.getString(key(account, folderId), null) ?: return FolderCleanupRule()
        val parts = stored.split(':')
        val size = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(1, 102400) ?: 500
        return FolderCleanupRule(size, parts.getOrNull(1) == "true")
    }

    override fun saveRule(account: String, folderId: String, rule: FolderCleanupRule) {
        require(account.isNotBlank() && folderId.isNotBlank())
        require(rule.maxSizeMb in 1..102400)
        prefs.edit().putString(key(account, folderId), "${rule.maxSizeMb}:${rule.enabled}").apply()
        lastChecked.remove(key(account, folderId))
    }

    private fun requireAccount(account: String, folderId: String) {
        check(account.isNotBlank() && repository.currentAccount == account) { "PikPak account changed" }
        check(folderId.isNotBlank() && !folderId.startsWith("local:")) { "Cloud folder required" }
    }

    private suspend fun listCandidates(
        account: String, folderId: String, maxSizeMb: Int
    ): List<FileStat> {
        require(maxSizeMb in 1..102400)
        requireAccount(account, folderId)
        val limit = maxSizeMb.toLong() * 1_000_000L
        // listAllFiles waits for every page: never decide based on just the visible page.
        val files = repository.listAllFiles(folderId).getOrThrow()
        requireAccount(account, folderId)
        return files.filter { file ->
            !file.isFolder && !file.trashed &&
                file.id.isNotBlank() && file.parentId == folderId &&
                file.sizeBytes > 0 && file.sizeBytes <= limit &&
                !isPikoInternalFolder(file, folderId)
        }
    }

    override suspend fun preview(account: String, folderId: String, maxSizeMb: Int): Result<List<FileStat>> =
        withContext(Dispatchers.IO) {
            try { Result.success(listCandidates(account, folderId, maxSizeMb)) }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { Result.failure(error) }
        }

    override suspend fun trashMatching(account: String, folderId: String, maxSizeMb: Int): Result<Int> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    // Re-scan immediately before trashing; never trust the preview snapshot.
                    val candidates = listCandidates(account, folderId, maxSizeMb)
                    var count = 0
                    for (batch in candidates.chunked(50)) {
                        requireAccount(account, folderId)
                        repository.moveToTrash(batch.map { it.id }).getOrThrow()
                        count += batch.size
                    }
                    PikoLog.i("FolderCleanup", "Moved $count small files to trash in folder $folderId")
                    Result.success(count)
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    PikoLog.w("FolderCleanup", "Cloud folder cleanup failed; no permanent delete performed", error)
                    Result.failure(error)
                }
            }
        }

    override suspend fun checkOnOpen(account: String, folderId: String): Result<Int>? {
        if (folderId.isBlank() || repository.currentAccount != account) return null
        val rule = getRule(account, folderId)
        if (!rule.enabled) return null
        val id = key(account, folderId)
        val now = android.os.SystemClock.elapsedRealtime()
        val previous = lastChecked[id]
        if (previous != null && now - previous in 0 until 60_000L) return null
        // Do not retry every recomposition / rapid folder navigation.
        lastChecked[id] = now
        return trashMatching(account, folderId, rule.maxSizeMb)
    }
}

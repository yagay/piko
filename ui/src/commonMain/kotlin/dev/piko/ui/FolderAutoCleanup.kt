package dev.piko.ui

import io.github.nihildigit.pikpak.FileStat

/** Optional Android-only cloud-folder cleanup; other Piko targets do not install an implementation. */
data class FolderCleanupRule(val maxSizeMb: Int = 500, val enabled: Boolean = false)

/** Cloud-only. Implementations must verify the account, listing and target type before trashing. */
interface FolderAutoCleanup {
    fun getRule(account: String, folderId: String): FolderCleanupRule
    fun saveRule(account: String, folderId: String, rule: FolderCleanupRule)
    suspend fun preview(account: String, folderId: String, maxSizeMb: Int): Result<List<FileStat>>
    suspend fun trashMatching(account: String, folderId: String, maxSizeMb: Int): Result<Int>
    suspend fun checkOnOpen(account: String, folderId: String): Result<Int>?
}

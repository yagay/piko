package dev.piko.desktop

import dev.piko.data.auth.PikoUserPreferences
import dev.piko.shared.auth.SecretVault
import dev.piko.shared.preferences.PreferenceSettings
import dev.piko.shared.preferences.StoredPikoPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 桌面持久化适配器，偏好键、默认值与机密访问策略由共享实现维护。 */
class DesktopPikoPreferences(
    private val settings: DesktopSettingsStore,
    openSecrets: () -> SecretVault,
) : PikoUserPreferences by StoredPikoPreferences(DesktopPreferenceSettings(settings), openSecrets) {
    private val magnetFilter = MutableStateFlow(settings.get("magnet.defaultFileSelection", "true:0:500"))
    override val magnetFileSelectionFilterFlow: Flow<String> = magnetFilter.asStateFlow()

    override suspend fun setMagnetFileSelectionFilter(value: String) {
        settings.set("magnet.defaultFileSelection", value)
        magnetFilter.value = value
    }
}

private class DesktopPreferenceSettings(private val store: DesktopSettingsStore) : PreferenceSettings {
    override fun get(key: String, default: String): String = store.get(key, default)
    override fun set(key: String, value: String) = store.set(key, value)
    override fun remove(key: String) = store.remove(key)
    override fun keysWithPrefix(prefix: String): List<String> = store.keysWithPrefix(prefix)
    override val downloadDirectoryPath: String get() = store.downloadDirectory.absolutePath
    override fun setDownloadDirectory(path: String) = store.setDownloadDirectory(path)
}

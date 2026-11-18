package dev.piko

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.piko.shared.state.InstantDraft
import dev.piko.shared.state.extractLinks
import dev.piko.ui.PikoApp
import dev.piko.ui.VideoPlayerHost
import dev.piko.ui.theme.appearanceFlow
import dev.piko.ui.theme.isDark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * An independent, floating Android entry point for ACTION_VIEW magnet/ed2k
 * and ACTION_SEND magnet text. It does not mount the main Piko scaffold.
 *
 * Accepted work stays in the process-level InstantSession after finish():
 * InstantDraft.close() retains its separate saveScope while the save executes.
 */
class ExternalMagnetActivity : ComponentActivity() {
    private var activeDraft: InstantDraft? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent
        val link = incomingLink(source)
        if (link == null) {
            forwardToMain(source)
            return
        }
        val app = PikoApplication.instance
        lifecycleScope.launch {
            app.clientManager.isInitializing.first { !it }
            if (isFinishing || isDestroyed) return@launch
            // Authentication needs the normal full-size app; don't create a draft
            // with an absent account, because the state snapshots the account.
            if (app.clientManager.currentClient.value == null) {
                forwardToMain(source)
                return@launch
            }
            val draft = app.instantSession.openDraft(link, app.services.saveTargets.context)
            activeDraft = draft
            val appearanceFlow = app.sessionManager.appearanceFlow(app.platform.supportsDynamicColor)
            val initialAppearance = runBlocking { appearanceFlow.first() }
            setContent {
                val appearance by appearanceFlow.collectAsStateWithLifecycle(initialAppearance)
                PikoApp(
                    services = app.services,
                    platform = app.platform,
                    appearance = appearance,
                    videoPlayer = VideoPlayerHost.InApp { _, _ -> },
                    externalImportDraft = draft,
                    onExternalImportDismiss = { dismiss() },
                    onExternalPreview = { _, _ ->
                        // Preview uses the full player host; the compact import
                        // surface never fakes a playback result.
                        startActivity(Intent(this@ExternalMagnetActivity, MainActivity::class.java))
                        dismiss()
                    },
                )
                LaunchedEffect(draft) {
                    snapshotFlow { draft.submitted.isNotEmpty() }.first { it }
                    // The save has been accepted and detached from its editor.
                    // Do not await completion, or cancel the processing scope.
                    dismiss()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        val horizontalMargin = (24f * metrics.density).toInt()
        val maxWidth = (560f * metrics.density).toInt()
        window.setLayout(
            (metrics.widthPixels - horizontalMargin).coerceAtMost(maxWidth),
            (metrics.heightPixels * 0.84f).toInt(),
        )
    }

    private fun dismiss() {
        activeDraft?.close()
        activeDraft = null
        finish()
    }

    override fun onDestroy() {
        activeDraft?.close()
        activeDraft = null
        super.onDestroy()
    }

    private fun forwardToMain(original: Intent) {
        val next = Intent(original).apply {
            setClass(this@ExternalMagnetActivity, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(next)
        finish()
    }

    private fun incomingLink(input: Intent): String? {
        val candidates = buildList {
            input.dataString?.let(::add)
            input.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let(::add)
            input.clipData?.let { clip ->
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).text?.toString()?.let(::add)
                }
            }
        }
        return candidates.flatMap(::extractLinks)
            .filter { it.isMagnet || it.uri.startsWith("ed2k://", ignoreCase = true) || it.uri.startsWith("thunder://", ignoreCase = true) }
            .distinctBy { it.key }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { it.uri }
    }
}

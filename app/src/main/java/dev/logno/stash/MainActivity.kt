package dev.logno.stash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val model: StashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The persisted draft/queue is authoritative after configuration or process recreation.
        if (savedInstanceState == null) receive(intent)
        setContent { StashApp(model) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        if (intent.getBooleanExtra("dev.logno.stash.SHARE_CONSUMED", false)) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        sharedDraft(text, subject)?.let(model::receiveShare)
        // A subsequent launcher visit must not process the same share a second time.
        intent.putExtra("dev.logno.stash.SHARE_CONSUMED", true)
    }
}

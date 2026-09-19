package dev.logno.stash

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun reset() {
        context.getSharedPreferences("stash", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun share(text: String) = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Test fun coldShareSurvivesRecreationAndWaitsForLogin() {
        ActivityScenario.launch<MainActivity>(share("https://example.com/article")).use { scenario ->
            compose.onNodeWithText("Your shared note is ready. Sign in to save it.").assertIsDisplayed()
            assertEquals("https://example.com/article", LocalStore(context).draft?.url)
            scenario.recreate()
            compose.onNodeWithText("Your shared note is ready. Sign in to save it.").assertIsDisplayed()
            assertEquals("https://example.com/article", LocalStore(context).draft?.url)
            assertTrue(LocalStore(context).queuedShares.isEmpty())
        }
    }

    @Test fun warmShareQueuesInsteadOfOverwritingExistingDraft() {
        LocalStore(context).draft = Draft(notes = "Unfinished note")
        // Keep the same intent filter identity: ActivityScenario tracks lifecycle events by
        // filterEquals, while onNewIntent correctly replaces the activity's current intent.
        ActivityScenario.launch<MainActivity>(share("")).use {
            compose.onNodeWithText("Your shared note is ready. Sign in to save it.").assertIsDisplayed()
            context.startActivity(share("https://example.com/second").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
            compose.waitUntil(5_000) { LocalStore(context).queuedShares.size == 1 }
            assertEquals("Unfinished note", LocalStore(context).draft?.notes)
            assertEquals("https://example.com/second", LocalStore(context).queuedShares.single().url)
        }
    }

    @Test fun keystoreTokenRoundTripsWithoutPlaintextStorage() {
        val store = LocalStore(context)
        store.token = "test-bearer-token"
        assertEquals("test-bearer-token", LocalStore(context).token)
        val raw = context.getSharedPreferences("stash", Context.MODE_PRIVATE).getString("token", "")!!
        assertFalse(raw.contains("test-bearer-token"))
        store.token = null
        assertNull(LocalStore(context).token)
    }

    @Test fun signedInDraftOpensEditorAndRendersMarkdownPreview() {
        LocalStore(context).apply {
            // An unreachable local port makes refresh fail promptly, without using a live account.
            server = "https://127.0.0.1:1"
            token = "test-token"
            draft = Draft(url = "https://example.com", notes = "# Heading\n\n**Bold** and ~~old~~\n\n- [ ] Task")
        }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            compose.onNodeWithText("New note").assertIsDisplayed()
            compose.onNodeWithText("https://example.com").assertIsDisplayed()
            compose.onNodeWithText("Preview").performClick()
            compose.waitForIdle()
            // Reaching Write again checks the native Markdown view was created without crashing.
            compose.onNodeWithText("Write").performClick()
            compose.onNodeWithText("Notes · Markdown supported").assertIsDisplayed()
        }
    }
}

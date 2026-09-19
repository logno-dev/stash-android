package dev.logno.stash

import android.text.Spanned
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.noties.markwon.core.spans.StrongEmphasisSpan
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownTest {
    @get:Rule val compose = createComposeRule()

    @Test fun softLineBreaksParagraphsAndCodeArePreservedAlongsideMarkdownFormatting() {
        val spacious = mutableStateOf(false)
        val markdown = "**First line**\nSecond line\nThird line\n\nNew paragraph\n\n```\nalpha\nbeta\n```"
        compose.setContent {
            MaterialTheme { Markdown(markdown, onError = {}, spacious = spacious.value) }
        }
        // Exercise the shared renderer in its card/preview and spacious detail configurations.
        for (detail in listOf(false, true)) {
            compose.runOnIdle { spacious.value = detail }
            compose.waitForIdle()
            onView(isAssignableFrom(TextView::class.java)).check { view, error ->
                if (error != null) throw error
                val text = (view as TextView).text as Spanned
                assertTrue(text.toString().contains("First line\nSecond line\nThird line"))
                assertTrue(text.toString().contains("\n\nNew paragraph"))
                assertTrue(text.toString().contains("alpha\nbeta"))
                assertFalse(text.toString().contains("**"))
                val bold = text.getSpans(0, text.length, StrongEmphasisSpan::class.java).single()
                assertEquals("First line", text.subSequence(text.getSpanStart(bold), text.getSpanEnd(bold)).toString())
            }
        }
    }
}

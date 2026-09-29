package dev.logno.stash

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkViewTest {
    @get:Rule val compose = createComposeRule()
    private val note = Bookmark(1, title = "Reading list", notes = "**Formatted body**\nSecond line\nThird line\n\n- Read a book",
        tags = "reading, personal", domain = "Notes", createdAt = "2026-09-19 10:00:00")
    private val link = Bookmark(2, url = "https://example.com/a/long/bookmark/path?source=reading",
        title = "Reading reference", domain = "example.com")

    @Test fun openUsesUnifiedNoteActionAndPreservesFiltersAcrossRestoration() {
        var opened: Bookmark? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                BookmarkBrowser(listOf(note, link), false, {}, {},
                    onEdit = { opened = it }, onAdd = {}, onError = {})
            }
        }
        compose.onNodeWithText("Notes only").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("reading")
        compose.onNodeWithTag("bookmark-${note.id}").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(note, opened)
        compose.onNodeWithText("#reading  #personal").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Notes only").assertIsSelected()
        compose.onNode(hasSetTextAction()).assertTextContains("reading")
        compose.onNodeWithText("Reading reference").assertDoesNotExist()
        compose.onNodeWithText("Open").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test fun linkCardKeepsBrowserActionAndUsesOneOpenAction() {
        var opened: Bookmark? = null
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                BookmarkBrowser(listOf(link), false, {}, {}, onEdit = { opened = it },
                    onAdd = {}, onError = {})
            }
        }
        compose.onNodeWithText(link.url!!).assertIsDisplayed()
        compose.onNodeWithTag("bookmark-${link.id}").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(link, opened)
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("View").assertDoesNotExist()
    }
}

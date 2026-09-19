package dev.logno.stash

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun viewIsReadOnlyRendersMarkdownAndPreservesFiltersAcrossBackAndRestoration() {
        var mutations = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                BookmarkBrowser(listOf(note, link), false, {}, {},
                    onEdit = { mutations++ }, onDelete = { mutations++ }, onAdd = { mutations++ }, onError = {})
            }
        }
        compose.onNodeWithText("Notes only").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("reading")
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText("Note details").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithText("#reading  #personal").assertIsDisplayed()
        onView(withText(containsString("Formatted body\nSecond line\nThird line"))).check(matches(isDisplayed()))
        assertEquals(0, mutations)

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Note details").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Notes only").assertIsSelected()
        compose.onNode(hasSetTextAction()).assertTextContains("reading")
        compose.onNodeWithText("Reading reference").assertDoesNotExist()
        compose.onNodeWithText("View").performClick()
        pressBack()
        compose.onNodeWithText("Note details").assertDoesNotExist()
        compose.onNodeWithText("View").assertIsDisplayed()
        assertEquals(0, mutations)
    }

    @Test fun linkWithoutNotesShowsFullUrlAndEditingRequiresExplicitAction() {
        var edited: Bookmark? = null
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                BookmarkBrowser(listOf(link), false, {}, {}, onEdit = { edited = it },
                    onDelete = {}, onAdd = {}, onError = {})
            }
        }
        compose.onNodeWithText("View").performClick()
        compose.onNodeWithText(link.url!!).assertIsDisplayed()
        compose.onNodeWithText("No notes added.").assertIsDisplayed()
        assertNull(edited)
        compose.onNodeWithText("Edit").performClick()
        assertEquals(link, edited)
    }
}

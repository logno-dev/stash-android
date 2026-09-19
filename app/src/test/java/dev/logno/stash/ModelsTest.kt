package dev.logno.stash

import org.junit.Assert.*
import org.junit.Test

class ModelsTest {
    @Test fun `shared browser link retains subject and accompanying text`() {
        val draft = sharedDraft("Read this: https://example.com/a?q=1&b=2", "Example")!!
        assertEquals("https://example.com/a?q=1&b=2", draft.url)
        assertEquals("Example\n\nRead this: https://example.com/a?q=1&b=2", draft.notes)
    }
    @Test fun `plain text share creates a standalone note`() {
        assertEquals(Draft(notes = "Remember the milk"), sharedDraft("Remember the milk", null))
        assertNull(sharedDraft(" ", null))
    }
    @Test fun `share strips surrounding punctuation but preserves balanced URL parentheses`() {
        assertEquals("https://example.com/wiki/Test_(thing)", sharedDraft("(https://example.com/wiki/Test_(thing)).", null)!!.url)
        assertEquals("https://a.test", sharedDraft("https://a.test and https://b.test", null)!!.url)
        assertTrue(sharedDraft("https://a.test and https://b.test", null)!!.notes.contains("https://b.test"))
    }
    @Test fun `draft validation rejects non web schemes and empty notes`() {
        assertNotNull(Draft(url = "javascript:alert(1)").validate())
        assertNotNull(Draft().validate())
        assertNull(Draft(notes = "note").validate())
        assertNull(Draft(url = "https://example.com").validate())
    }
    @Test fun `search spans fields tolerates typos and combines notes filter`() {
        val link = Bookmark(1, "https://example.com", "Kotlin reference", "Compose UI", "android", "example.com")
        val note = Bookmark(2, title = "Note", notes = "Kotlin ideas", domain = "Notes")
        val all = listOf(link, note)
        assertEquals(listOf(link), searchBookmarks(all, "andriod", false))
        assertEquals(listOf(link), searchBookmarks(all, "compose example", false))
        assertEquals(listOf(note), searchBookmarks(all, "kotlin", true))
        assertTrue(searchBookmarks(all, "unrelated", false).isEmpty())
    }
}

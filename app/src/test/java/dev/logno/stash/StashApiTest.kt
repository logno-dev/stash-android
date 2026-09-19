package dev.logno.stash

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StashApiTest {
    private lateinit var server: MockWebServer
    private val api = StashApi()
    private val bookmark = """{"id":1,"url":null,"title":"Note","notes":"hello","tags":"","domain":"Notes","userId":"ignored"}"""
    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }
    private fun address() = server.url("/").toString().trimEnd('/')

    @Test fun `login uses existing API contract`() = runTest {
        server.enqueue(MockResponse().setBody("""{"token":"session","user":{"id":"1"}}"""))
        assertEquals("session", api.login(address(), "a@example.com", "secret"))
        val request = server.takeRequest()
        assertEquals("/api/auth/login", request.path)
        assertEquals("POST", request.method)
        assertEquals("a@example.com", Json.parseToJsonElement(request.body.readUtf8()).jsonObject["email"]!!.jsonPrimitive.content)
        assertNull(request.getHeader("Authorization"))
    }
    @Test fun `grouped bookmarks flatten and bearer token is sent`() = runTest {
        server.enqueue(MockResponse().setBody("""{"Notes":[$bookmark]}"""))
        assertEquals("hello", api.bookmarks(address(), "session").single().notes)
        val request = server.takeRequest()
        assertEquals("/api/bookmarks?all=true", request.path)
        assertEquals("Bearer session", request.getHeader("Authorization"))
    }
    @Test fun `create and update serialize nullable links and correct methods`() = runTest {
        for (id in listOf(null, 1)) {
            server.enqueue(MockResponse().setBody(bookmark))
            api.save(address(), "session", Draft(id = id, notes = " hello "))
            val request = server.takeRequest()
            assertEquals(if (id == null) "POST" else "PUT", request.method)
            assertEquals(if (id == null) "/api/bookmarks" else "/api/bookmarks/1", request.path)
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals(JsonNull, body["url"])
            assertEquals("hello", body["notes"]!!.jsonPrimitive.content)
        }
    }
    @Test fun `unauthorized response retains status for session expiry handling`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Unauthorized"}"""))
        try { api.bookmarks(address(), "expired"); fail("Expected unauthorized") }
        catch (error: ApiException) { assertEquals(401, error.status); assertEquals("Unauthorized", error.message) }
    }
    @Test fun `delete and registration use expected payloads`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        api.delete(address(), "session", 7)
        val deletion = server.takeRequest()
        assertEquals("DELETE", deletion.method)
        assertEquals("/api/bookmarks/7", deletion.path)
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        api.register(address(), "a@b.com", "pass", "pass", "First", "Last")
        val registration = server.takeRequest()
        assertEquals("/api/auth/register", registration.path)
        assertEquals("pass", Json.parseToJsonElement(registration.body.readUtf8()).jsonObject["confirmPassword"]!!.jsonPrimitive.content)
    }
}

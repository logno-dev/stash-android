package dev.logno.stash

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, message: String) : IOException(message)

class StashApi(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
    .callTimeout(60, TimeUnit.SECONDS).followRedirects(false).build()) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun login(server: String, email: String, password: String): String {
        val result = request(server, "auth/login", body = buildJsonObject {
            put("email", email); put("password", password)
        })
        return result.jsonObject["token"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: throw IOException("The server returned no login token.")
    }

    suspend fun register(server: String, email: String, password: String, confirm: String, first: String, last: String) {
        request(server, "auth/register", body = buildJsonObject {
            put("email", email); put("password", password); put("confirmPassword", confirm)
            put("firstName", first); put("lastName", last)
        })
    }

    suspend fun bookmarks(server: String, token: String): List<Bookmark> =
        request(server, "bookmarks?all=true", token).jsonObject.values.flatMap { group ->
            json.decodeFromJsonElement<List<Bookmark>>(group)
        }

    suspend fun save(server: String, token: String, draft: Draft): Bookmark {
        val response = request(server, "bookmarks" + (draft.id?.let { "/$it" } ?: ""), token,
            if (draft.id == null) "POST" else "PUT", buildJsonObject {
                put("url", draft.url.trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull)
                put("notes", draft.notes.trim()); put("tags", draft.tags.trim())
            })
        return json.decodeFromJsonElement(response)
    }

    suspend fun delete(server: String, token: String, id: Int) {
        request(server, "bookmarks/$id", token, "DELETE")
    }

    private suspend fun request(server: String, path: String, token: String? = null,
        method: String? = null, body: JsonObject? = null): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${server.trimEnd('/')}/api/$path")
            .header("Accept", "application/json")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .method(method ?: if (body == null) "GET" else "POST", body?.toString()?.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            if (!response.isSuccessful) {
                val message = (parsed as? JsonObject)?.get("error")?.jsonPrimitive?.contentOrNull
                throw ApiException(response.code, message ?: "Server request failed (${response.code}).")
            }
            parsed ?: throw IOException("The server returned an invalid response. Check the server address.")
        }
    }
}

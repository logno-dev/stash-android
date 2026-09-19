package dev.logno.stash

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class StashViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val api = StashApi()
    private var token = store.token
    var server by mutableStateOf(store.server); private set
    var loggedIn by mutableStateOf(token != null); private set
    var bookmarks by mutableStateOf<List<Bookmark>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var draft by mutableStateOf(store.draft); private set
    private var shares = store.queuedShares.toMutableList()
    var queuedCount by mutableIntStateOf(shares.size); private set

    init {
        if (draft == null) nextDraft()
        if (loggedIn) refresh()
    }

    fun clearMessage() { message = null }
    fun notify(text: String) { message = text }
    fun edit(value: Draft) { draft = value; store.draft = value }
    fun receiveShare(value: Draft) {
        if (draft == null) edit(value)
        else {
            shares.add(value); persistShares()
            message = "Shared note queued. Finish this note to open it."
        }
    }
    fun closeDraft() {
        draft = null; store.draft = null
        nextDraft()
    }
    private fun persistShares() { store.queuedShares = shares; queuedCount = shares.size }
    private fun nextDraft() {
        if (shares.isNotEmpty()) { edit(shares.removeAt(0)); persistShares() }
    }

    fun authenticate(address: String, email: String, password: String, registering: Boolean,
        confirm: String, first: String, last: String, onRegistered: () -> Unit) {
        val normalized = address.trim().trimEnd('/')
        if (!isWebUrl(normalized) || !normalized.startsWith("https://", true) ||
            java.net.URI(normalized).let { it.rawUserInfo != null || it.rawQuery != null || it.rawFragment != null }) {
            message = "Enter your Stash server’s HTTPS address, without a query or fragment."; return
        }
        if (email.isBlank() || password.isBlank()) { message = "Enter your email and password."; return }
        if (registering && (first.isBlank() || last.isBlank() || password != confirm)) {
            message = "Enter both names and matching passwords."; return
        }
        runOperation {
            if (registering) {
                api.register(normalized, email.trim(), password, confirm, first.trim(), last.trim())
                message = "Account created. You can now sign in."
                onRegistered()
            } else {
                val newToken = api.login(normalized, email.trim(), password)
                // Persist credentials before making the authenticated UI visible.
                store.token = newToken; store.server = normalized
                server = normalized; token = newToken; loggedIn = true
                bookmarks = api.bookmarks(server, newToken)
            }
        }
    }

    fun refresh() = runOperation { bookmarks = api.bookmarks(server, requireNotNull(token)) }

    fun save() {
        val value = draft ?: return
        value.validate()?.let { message = it; return }
        runOperation {
            val saved = api.save(server, requireNotNull(token), value)
            bookmarks = listOf(saved) + bookmarks.filter { it.id != saved.id }
            closeDraft()
            message = "Saved to Stash."
        }
    }

    fun delete(bookmark: Bookmark, onDeleted: () -> Unit) = runOperation {
        api.delete(server, requireNotNull(token), bookmark.id)
        bookmarks = bookmarks.filter { it.id != bookmark.id }
        onDeleted()
        message = "Deleted."
    }

    fun logout() {
        if (busy) return
        token = null; store.token = null; loggedIn = false; bookmarks = emptyList()
        draft = null; store.draft = null; shares.clear(); persistShares()
    }

    private fun runOperation(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (error is ApiException && error.status == 401 && loggedIn) {
                    token = null; store.token = null; loggedIn = false; bookmarks = emptyList()
                    message = "Your session expired. Sign in again; your draft is kept."
                } else {
                    message = error.message ?: "Could not reach Stash. Please try again."
                }
            } finally { busy = false }
        }
    }
}

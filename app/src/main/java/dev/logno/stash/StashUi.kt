package dev.logno.stash

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val StashColors = darkColorScheme(
    primary = Color(0xFFBBC7DB), onPrimary = Color(0xFF202C3E),
    secondary = Color(0xFFBFC5CF), background = Color(0xFF101113),
    surface = Color(0xFF101113), surfaceVariant = Color(0xFF22252A),
    onSurface = Color(0xFFE4E5E9), onSurfaceVariant = Color(0xFFB8BBC3),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StashApp(model: StashViewModel) {
    MaterialTheme(colorScheme = StashColors) {
        val snackbar = remember { SnackbarHostState() }
        val stateHolder = rememberSaveableStateHolder()
        LaunchedEffect(model.message) {
            model.message?.let {
                snackbar.showSnackbar(it, withDismissAction = true)
                model.clearMessage()
            }
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            modifier = Modifier.imePadding(),
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                    if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    when {
                        !model.loggedIn -> AuthScreen(model)
                        model.draft != null -> stateHolder.SaveableStateProvider("note") { NoteScreen(model) }
                        else -> stateHolder.SaveableStateProvider("bookmarks") { BookmarkScreen(model) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthScreen(model: StashViewModel) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf(model.server) }
    var email by rememberSaveable { mutableStateOf("") }
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MustacheLogo()
            Text("Stash", style = MaterialTheme.typography.displaySmall)
        }
        Text(if (registering) "Create your account" else "Your links. Your notes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (model.draft != null) Text("Your shared note is ready. Sign in to save it.", color = MaterialTheme.colorScheme.primary)
        Field(address, { address = it }, "Server address", model.busy, KeyboardType.Uri)
        Field(email, { email = it }, "Email", model.busy, KeyboardType.Email)
        if (registering) {
            Field(first, { first = it }, "First name", model.busy)
            Field(last, { last = it }, "Last name", model.busy)
        }
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), enabled = !model.busy,
            label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (registering) OutlinedTextField(confirm, { confirm = it }, Modifier.fillMaxWidth(), enabled = !model.busy,
            label = { Text("Confirm password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Button(onClick = {
            model.authenticate(address, email, password, registering, confirm, first, last) {
                registering = false; password = ""; confirm = ""
            }
        }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (registering) "Create account" else "Sign in")
        }
        TextButton(onClick = { registering = !registering; confirm = "" }, enabled = !model.busy) {
            Text(if (registering) "Already have an account? Sign in" else "Create an account")
        }
    }
}

@Composable
internal fun Field(value: String, change: (String) -> Unit, label: String, busy: Boolean, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) },
        enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}

@Composable
private fun MustacheLogo() {
    Icon(painterResource(R.drawable.ic_mustache), contentDescription = null,
        modifier = Modifier.width(42.dp).height(16.dp), tint = MaterialTheme.colorScheme.primary)
}

@Composable
private fun BookmarkScreen(model: StashViewModel) {
    var logout by remember { mutableStateOf(false) }
    BookmarkBrowser(
        bookmarks = model.bookmarks, busy = model.busy,
        onRefresh = model::refresh, onSignOut = { logout = true },
        onEdit = { model.edit(Draft(it.id, it.url.orEmpty(), it.notes.orEmpty(), it.tags.orEmpty())) },
        onAdd = { model.edit(Draft()) }, onError = model::notify,
    )
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("Sign out?") },
        text = { Text("You can sign back in to access your stash.") },
        confirmButton = { TextButton(onClick = { model.logout(); logout = false }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { logout = false }) { Text("Cancel") } })
}

@Composable
internal fun BookmarkBrowser(
    bookmarks: List<Bookmark>, busy: Boolean,
    onRefresh: () -> Unit, onSignOut: () -> Unit, onEdit: (Bookmark) -> Unit,
    onAdd: () -> Unit, onError: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var notesOnly by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val filtered = remember(bookmarks, query, notesOnly) { searchBookmarks(bookmarks, query, notesOnly) }
    val groups = remember(filtered) { filtered.groupBy { it.domain }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MustacheLogo()
                Spacer(Modifier.width(10.dp))
                Text("Stash", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onRefresh, enabled = !busy) { Text("Refresh") }
                TextButton(onClick = onSignOut, enabled = !busy) { Text("Sign out") }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search links, notes, tags…") }, singleLine = true,
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear") } })
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = notesOnly, onClick = { notesOnly = !notesOnly }, label = { Text("Notes only") })
                Spacer(Modifier.weight(1f))
                Text("${filtered.size} items", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (filtered.isEmpty()) item {
                    Text(if (busy) "Loading your stash…" else if (query.isNotBlank()) "No matching notes or links."
                        else if (notesOnly) "No notes without links yet." else "Your stash is empty. Add a note or share a link from another app.",
                        Modifier.padding(vertical = 40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                groups.forEach { (domain, bookmarks) ->
                    item(key = "domain:$domain") {
                        Text("$domain · ${bookmarks.size}", Modifier.padding(top = 12.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    items(bookmarks, key = { it.id }, contentType = { "bookmark" }) { bookmark ->
                        BookmarkCard(bookmark, busy,
                            onOpen = { openLink(context, bookmark.url.orEmpty(), onError) },
                            onSelect = { onEdit(bookmark) })
                    }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { if (!busy) onAdd() },
            Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Text("+  New note") }
    }
}

@Composable
private fun BookmarkCard(bookmark: Bookmark, busy: Boolean, onOpen: () -> Unit, onSelect: () -> Unit) {
    Card(onClick = onSelect, enabled = !busy,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C20)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(bookmark.title, style = MaterialTheme.typography.titleMedium)
            if (!bookmark.url.isNullOrBlank()) {
                TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
                    Text(bookmark.url, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!bookmark.notes.isNullOrBlank()) Text(bookmark.notes, maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!bookmark.tags.isNullOrBlank()) Text(bookmark.tags.split(',').map(String::trim).filter(String::isNotEmpty)
                .joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(displayDate(bookmark.createdAt), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun displayDate(raw: String?): String = runCatching {
    LocalDate.parse(raw.orEmpty().take(10)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}.getOrDefault("")

internal fun openLink(context: Context, url: String, onError: (String) -> Unit) {
    if (!isWebUrl(url)) { onError("Only HTTP and HTTPS links can be opened."); return }
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (_: ActivityNotFoundException) { onError("No browser is available to open this link.") }
}

@Composable
private fun NoteScreen(model: StashViewModel) {
    val draft = model.draft ?: return
    val original = remember(draft.id) { draft }
    val bookmark = draft.id?.let { id -> model.bookmarks.firstOrNull { it.id == id } }
    var preview by rememberSaveable(draft.id) { mutableStateOf(false) }
    var showDetails by rememberSaveable(draft.id) { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable(draft.id) { mutableStateOf(false) }
    fun close() {
        if (draft == original) model.closeDraft() else discard = true
    }
    BackHandler { if (!model.busy) close() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(bookmark?.title ?: if (draft.id == null) "New note" else "Note", Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = ::close, enabled = !model.busy) { Text(if (draft.id == null) "Cancel" else "Close") }
            Button(onClick = model::save, enabled = !model.busy) { Text("Save") }
        }
        if (model.queuedCount > 0) Text("${model.queuedCount} shared notes waiting", color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !preview, onClick = { preview = false }, label = { Text("Write") })
            FilterChip(selected = preview, onClick = { preview = true }, label = { Text("Preview") })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showHelp = true }) { Text("Markdown help") }
        }
        if (preview) {
            Surface(Modifier.fillMaxWidth().weight(1f), color = Color(0xFF1A1C20), shape = MaterialTheme.shapes.medium) {
                Box(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Markdown(draft.notes.ifBlank { "No notes added." }, model::notify, spacious = true)
                }
            }
        } else {
            OutlinedTextField(draft.notes, { model.edit(draft.copy(notes = it)) },
                Modifier.fillMaxWidth().weight(1f), enabled = !model.busy,
                label = { Text("Notes · Markdown supported") })
        }
        TextButton(onClick = { showDetails = !showDetails }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(if (showDetails) "Hide details" else "${if (draft.url.isBlank()) "Note" else "Bookmark"} details")
        }
        if (showDetails) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(draft.url, { model.edit(draft.copy(url = it)) }, "Link (optional)", model.busy, KeyboardType.Uri)
                Field(draft.tags, { model.edit(draft.copy(tags = it)) }, "Tags (comma separated)", model.busy)
                bookmark?.let {
                    Text(listOf(it.domain, displayDate(it.createdAt)).filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { deleting = true }, enabled = !model.busy,
                        contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Delete note") }
                }
            }
        }
    }
    if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text("Markdown help") },
        text = { Text("**bold**  *italic*  ~~strikethrough~~\n# Heading   - List   - [ ] Task\n[link](https://...)   `code`   > Quote") },
        confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Close") } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard changes?") },
        text = { Text("Your unsaved changes will be removed.") },
        confirmButton = { TextButton(onClick = { discard = false; model.closeDraft() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
    if (deleting && bookmark != null) AlertDialog(onDismissRequest = { if (!model.busy) deleting = false },
        title = { Text("Delete note?") }, text = { Text("“${bookmark.title}” will be permanently deleted.") },
        confirmButton = { TextButton(onClick = { model.delete(bookmark) { deleting = false; model.closeDraft() } },
            enabled = !model.busy) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }, enabled = !model.busy) { Text("Cancel") } })
}

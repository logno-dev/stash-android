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
                        model.draft != null -> EditorScreen(model)
                        else -> BookmarkScreen(model)
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
    var deleting by remember { mutableStateOf<Bookmark?>(null) }
    var logout by remember { mutableStateOf(false) }
    BookmarkBrowser(
        bookmarks = model.bookmarks, busy = model.busy,
        onRefresh = model::refresh, onSignOut = { logout = true },
        onEdit = { model.edit(Draft(it.id, it.url.orEmpty(), it.notes.orEmpty(), it.tags.orEmpty())) },
        onDelete = { deleting = it }, onAdd = { model.edit(Draft()) }, onError = model::notify,
    )
    deleting?.let { bookmark ->
        AlertDialog(onDismissRequest = { if (!model.busy) deleting = null },
            title = { Text("Delete note?") }, text = { Text("“${bookmark.title}” will be permanently deleted.") },
            confirmButton = { TextButton(onClick = { model.delete(bookmark) { deleting = null } }, enabled = !model.busy) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }, enabled = !model.busy) { Text("Cancel") } })
    }
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("Sign out?") },
        text = { Text("You can sign back in to access your stash.") },
        confirmButton = { TextButton(onClick = { model.logout(); logout = false }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { logout = false }) { Text("Cancel") } })
}

@Composable
internal fun BookmarkBrowser(
    bookmarks: List<Bookmark>, busy: Boolean,
    onRefresh: () -> Unit, onSignOut: () -> Unit, onEdit: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit, onAdd: () -> Unit, onError: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var notesOnly by rememberSaveable { mutableStateOf(false) }
    var viewingId by rememberSaveable { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val filtered = remember(bookmarks, query, notesOnly) { searchBookmarks(bookmarks, query, notesOnly) }
    val groups = remember(filtered) { filtered.groupBy { it.domain }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    val context = LocalContext.current

    val viewing = bookmarks.firstOrNull { it.id == viewingId }
    if (viewing != null) {
        BookmarkDetails(viewing, busy, onBack = { viewingId = null }, onEdit = { onEdit(viewing) }, onError = onError)
        return
    }

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
                            onView = { viewingId = bookmark.id },
                            onEdit = { onEdit(bookmark) },
                            onDelete = { onDelete(bookmark) }, onError = onError)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { if (!busy) onAdd() },
            Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Text("+  New note") }
    }
}

@Composable
private fun BookmarkCard(bookmark: Bookmark, busy: Boolean, onOpen: () -> Unit, onView: () -> Unit, onEdit: () -> Unit,
    onDelete: () -> Unit, onError: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C20)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(bookmark.title, style = MaterialTheme.typography.titleMedium)
            if (!bookmark.url.isNullOrBlank()) {
                TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
                    Text(bookmark.url, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!bookmark.notes.isNullOrBlank()) Markdown(bookmark.notes, onError)
            if (!bookmark.tags.isNullOrBlank()) Text(bookmark.tags.split(',').map(String::trim).filter(String::isNotEmpty)
                .joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(displayDate(bookmark.createdAt), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onView) { Text("View") }
                TextButton(onClick = onEdit, enabled = !busy) { Text("Edit") }
                TextButton(onClick = onDelete, enabled = !busy) { Text("Delete") }
            }
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
private fun EditorScreen(model: StashViewModel) {
    val draft = model.draft ?: return
    var preview by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (!model.busy) discard = true }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (draft.id == null) "New note" else "Edit note", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { discard = true }, enabled = !model.busy) { Text("Cancel") }
            Button(onClick = model::save, enabled = !model.busy) { Text("Save") }
        }
        if (model.queuedCount > 0) Text("${model.queuedCount} shared notes waiting", color = MaterialTheme.colorScheme.primary)
        Field(draft.url, { model.edit(draft.copy(url = it)) }, "Link (optional)", model.busy, KeyboardType.Uri)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !preview, onClick = { preview = false }, label = { Text("Write") })
            FilterChip(selected = preview, onClick = { preview = true }, label = { Text("Preview") })
        }
        if (preview) {
            Surface(Modifier.fillMaxWidth().heightIn(min = 220.dp), color = Color(0xFF1A1C20), shape = MaterialTheme.shapes.medium) {
                Box(Modifier.padding(16.dp)) { Markdown(draft.notes.ifBlank { "Nothing to preview." }, model::notify) }
            }
        } else {
            OutlinedTextField(draft.notes, { model.edit(draft.copy(notes = it)) },
                Modifier.fillMaxWidth().heightIn(min = 220.dp), enabled = !model.busy,
                label = { Text("Notes · Markdown supported") }, minLines = 8)
            Text("**bold**  *italic*  ~~strikethrough~~\n# Heading   - List   - [ ] Task\n[link](https://…)   `code`   > Quote",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Field(draft.tags, { model.edit(draft.copy(tags = it)) }, "Tags (comma separated)", model.busy)
        Text("Links get their title automatically. Leave the link empty to create a standalone note.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard this draft?") },
        text = { Text("Your unsaved changes will be removed.") },
        confirmButton = { TextButton(onClick = { discard = false; model.closeDraft() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
}

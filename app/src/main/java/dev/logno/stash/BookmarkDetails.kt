package dev.logno.stash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun BookmarkDetails(
    bookmark: Bookmark, busy: Boolean, onBack: () -> Unit,
    onEdit: () -> Unit, onError: (String) -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Note details", Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onEdit, enabled = !busy) { Text("Edit") }
        }
        HorizontalDivider()
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SelectionContainer {
                Text(bookmark.title, style = MaterialTheme.typography.headlineSmall)
            }
            Text(listOf(bookmark.domain, displayDate(bookmark.createdAt)).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!bookmark.url.isNullOrBlank()) {
                TextButton(onClick = { openLink(context, bookmark.url, onError) }, contentPadding = PaddingValues(0.dp)) {
                    Text(bookmark.url, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (!bookmark.tags.isNullOrBlank()) {
                SelectionContainer {
                    Text(bookmark.tags.split(',').map(String::trim).filter(String::isNotEmpty).joinToString("  ") { "#$it" },
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider()
            if (bookmark.notes.isNullOrBlank()) {
                Text("No notes added.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Markdown(bookmark.notes, onError, spacious = true)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

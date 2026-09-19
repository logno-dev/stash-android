package dev.logno.stash

import android.graphics.Color
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin

@Composable
fun Markdown(content: String, onError: (String) -> Unit) {
    val context = LocalContext.current
    val currentError = rememberUpdatedState(onError)
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(GlideImagesPlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { _, link -> openLink(context, link, currentError.value) }
                }
            }).build()
    }
    AndroidView(modifier = Modifier.fillMaxWidth(), factory = { ctx ->
        TextView(ctx).apply {
            setTextColor(Color.rgb(210, 212, 218))
            setLinkTextColor(Color.rgb(187, 199, 219))
            textSize = 15f
            setTextIsSelectable(true)
        }
    }, update = { view ->
        if (view.tag != content) {
            markwon.setMarkdown(view, content)
            view.tag = content
        }
    })
}

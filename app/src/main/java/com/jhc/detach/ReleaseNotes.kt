// Added by skofqq in 2026. Part of a modified version of j-hc/zygisk-detach-app (Apache-2.0).
package com.jhc.detach

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Asks before installing an update, showing what changed in it. */
@Composable
fun UpdateDialog(update: UpdateState.Available, onInstall: () -> Unit, onDismiss: () -> Unit) {
    val headingColor = MaterialTheme.colorScheme.primary
    val notes = remember(update.notes, headingColor) { renderNotes(update.notes, headingColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_whats_new, update.version)) },
        text = {
            Text(
                notes.takeIf { it.isNotEmpty() }
                    ?: AnnotatedString(stringResource(R.string.update_no_notes)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onInstall) { Text(stringResource(R.string.update_install)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
        },
    )
}

private val inline = Regex("""\*\*(.+?)\*\*|\[([^\]]+)]\([^)]*\)|`([^`]+)`""")

/**
 * The bit of Markdown that release notes use: headings, bullets, **bold**, links and `code`.
 * Links keep only their text, since the dialog is not the place to leave the app.
 */
private fun renderNotes(markdown: String, headingColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        val lines = markdown.lines().map { it.trimEnd() }.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        var blank = false
        lines.forEachIndexed { index, raw ->
            if (raw.isBlank()) {
                blank = true
                return@forEachIndexed
            }
            if (index > 0) append(if (blank) "\n\n" else "\n")
            blank = false
            val line = raw.trimStart()
            when {
                line.startsWith("#") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = headingColor)) {
                    appendInline(line.trimStart('#').trim())
                }
                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> {
                    append("•  ")
                    appendInline(line.substring(2).trim())
                }
                else -> appendInline(line)
            }
        }
    }

private fun AnnotatedString.Builder.appendInline(text: String) {
    var last = 0
    for (match in inline.findAll(text)) {
        append(text.substring(last, match.range.first))
        val (bold, link, code) = match.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            link.isNotEmpty() -> append(link)
            else -> append(code)
        }
        last = match.range.last + 1
    }
    append(text.substring(last))
}

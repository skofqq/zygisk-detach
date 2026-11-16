// Added by skofqq in 2026. Part of a modified version of j-hc/zygisk-detach-app (Apache-2.0).
package com.jhc.detach

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Something about the module the user should know before relying on the detach list. */
sealed class ModuleNotice(
    @StringRes val title: Int,
    @StringRes val action: Int,
    /** Problems that stop detaching from working, as opposed to advice. */
    val critical: Boolean,
) {
    data object Disabled : ModuleNotice(R.string.module_disabled_title, R.string.module_action_enable, true)
    data object Reboot : ModuleNotice(R.string.module_reboot_title, R.string.module_action_reboot, true)
    data object Removing : ModuleNotice(R.string.module_removing_title, R.string.module_action_keep, true)
    data object ZygiskOff : ModuleNotice(R.string.module_zygisk_off_title, 0, true)
    data class Outdated(val current: String, val latest: String) :
        ModuleNotice(R.string.module_outdated_title, R.string.module_action_releases, false)
}

@Composable
private fun ModuleNotice.text(): String = when (this) {
    ModuleNotice.Disabled -> stringResource(R.string.module_disabled_text)
    ModuleNotice.Reboot -> stringResource(R.string.module_reboot_text)
    ModuleNotice.Removing -> stringResource(R.string.module_removing_text)
    ModuleNotice.ZygiskOff -> stringResource(R.string.module_zygisk_off_text)
    is ModuleNotice.Outdated -> stringResource(R.string.module_outdated_text, latest, current)
}

@Composable
fun ModuleNotices(
    notices: List<ModuleNotice>,
    onAction: (ModuleNotice) -> Unit,
    onDismiss: (ModuleNotice) -> Unit,
) {
    Column {
        // every kind keeps its own slot so a notice animates in and out instead of popping
        for (kind in listOf("problem", "outdated")) {
            val notice = notices.firstOrNull { (it is ModuleNotice.Outdated) == (kind == "outdated") }
            AnimatedVisibility(
                visible = notice != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (notice != null) {
                    NoticeCard(notice, onAction, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(
    notice: ModuleNotice,
    onAction: (ModuleNotice) -> Unit,
    onDismiss: (ModuleNotice) -> Unit,
) {
    val container = if (notice.critical) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (notice.critical) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onTertiaryContainer
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = container,
        contentColor = content,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (notice.critical) Icons.Filled.Warning else Icons.Filled.Info,
                    contentDescription = null,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(notice.title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (notice is ModuleNotice.Outdated) {
                    IconButton(onClick = { onDismiss(notice) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.module_action_later))
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
            Text(
                notice.text(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, end = 12.dp)
            )
            if (notice.action != 0) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { onAction(notice) }) {
                        Text(stringResource(notice.action), color = content)
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Shown instead of the list when the module isn't there to read the detach list from. */
@Composable
fun NoModuleScreen(
    pendingReboot: Boolean,
    innerPadding: PaddingValues,
    onDownload: () -> Unit,
    onReboot: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
            Icon(
                if (pendingReboot) Icons.Filled.Refresh else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (pendingReboot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(if (pendingReboot) R.string.module_reboot_title else R.string.error_no_module),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (pendingReboot) R.string.module_installed_reboot_text else R.string.module_missing_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = if (pendingReboot) onReboot else onDownload) {
                Text(stringResource(if (pendingReboot) R.string.module_action_reboot else R.string.module_action_download))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.module_action_retry))
            }
        }
    }
}

@Composable
fun RebootDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.module_reboot_confirm_title)) },
        text = { Text(stringResource(R.string.module_reboot_confirm_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.module_action_reboot)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

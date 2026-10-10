package com.jhc.detach

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.jhc.detach.ui.theme.AppTheme
import com.jhc.detach.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/** Where this app is published; Obtainium tracks its GitHub releases. */
private const val REPO_URL = "https://github.com/skofqq/zygisk-detach"
private const val OBTAINIUM_URL = "https://github.com/ImranR98/Obtainium/releases/latest"

private class SettingsItem(
    val icon: ImageVector,
    @StringRes val title: Int,
    val summary: String,
    val progress: Float? = null,
    val onClick: () -> Unit,
)

private fun Context.openUri(uri: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val obtainiumMissing = stringResource(R.string.obtainium_missing)

    val update = Updater.state
    val updateSummary = when (update) {
        UpdateState.Idle -> stringResource(R.string.update_idle, version)
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.UpToDate -> stringResource(R.string.update_latest, version)
        is UpdateState.Available -> stringResource(R.string.update_available, update.version)
        is UpdateState.Downloading ->
            stringResource(R.string.update_downloading, update.version, (update.progress * 100).toInt())
        is UpdateState.Installing -> stringResource(R.string.update_installing, update.version)
        is UpdateState.Failed ->
            if (update.signatureMismatch) stringResource(R.string.update_signature)
            else stringResource(R.string.update_failed, update.message)
    }
    val updates = listOf(
        SettingsItem(
            Icons.Filled.Refresh,
            R.string.update_check,
            updateSummary,
            progress = when (update) {
                is UpdateState.Downloading -> update.progress
                UpdateState.Checking, is UpdateState.Installing -> -1f
                else -> null
            }
        ) {
            when {
                update is UpdateState.Available -> Updater.downloadAndInstall(context)
                update is UpdateState.Failed && update.signatureMismatch ->
                    context.openUri("$REPO_URL/releases/latest")
                else -> Updater.check(version)
            }
        },
        SettingsItem(
            Icons.Filled.Add,
            R.string.settings_obtainium,
            stringResource(R.string.settings_obtainium_summary)
        ) {
            // obtainium://add/<url> opens Obtainium's "add app" screen prefilled with the repo
            if (!context.openUri("obtainium://add/$REPO_URL")) {
                scope.launch { snackbarHostState.showSnackbar(obtainiumMissing) }
                context.openUri(OBTAINIUM_URL)
            }
        },
        SettingsItem(
            Icons.Filled.Share,
            R.string.settings_releases,
            "$REPO_URL/releases"
        ) { context.openUri("$REPO_URL/releases") },
    )
    val about = listOf(
        SettingsItem(Icons.Filled.Info, R.string.settings_version, version) {},
        SettingsItem(Icons.Filled.Star, R.string.settings_source, REPO_URL) {
            context.openUri(REPO_URL)
        },
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            appearanceGroup()
            settingsGroup(R.string.settings_updates, updates)
            settingsGroup(R.string.settings_about, about)
        }
    }
}

private fun LazyListScope.groupTitle(@StringRes title: Int) {
    item(key = "title-$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp)
        )
    }
}

private fun LazyListScope.appearanceGroup() {
    groupTitle(R.string.settings_appearance)
    val count = if (AppTheme.supportsDynamicColor) 2 else 1
    item(key = "theme") {
        Surface(
            shape = segmentShape(0, count),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_contrast),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = AppTheme.mode == mode,
                            onClick = { AppTheme.updateMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                        ) { Text(stringResource(mode.label), maxLines = 1) }
                    }
                }
            }
        }
    }
    if (AppTheme.supportsDynamicColor) item(key = "dynamic-color") {
        Surface(
            shape = segmentShape(1, count),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = AppTheme.dynamicColor,
                    role = Role.Switch,
                    onValueChange = AppTheme::updateDynamicColor
                )
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(painterResource(R.drawable.ic_palette), contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_color_summary)) },
                trailingContent = { Switch(checked = AppTheme.dynamicColor, onCheckedChange = null) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.settingsGroup(@StringRes title: Int, items: List<SettingsItem>) {
    groupTitle(title)
    itemsIndexed(items, key = { _, item -> item.title }) { index, item ->
        Surface(
            shape = segmentShape(index, items.size),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = item.onClick)
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(item.icon, contentDescription = null) },
                headlineContent = { Text(stringResource(item.title)) },
                supportingContent = {
                    Column {
                        Text(item.summary)
                        item.progress?.let { progress ->
                            val modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                            if (progress < 0f) LinearWavyProgressIndicator(modifier)
                            else LinearWavyProgressIndicator(progress = { progress }, modifier = modifier)
                        }
                    }
                },
            )
        }
    }
}

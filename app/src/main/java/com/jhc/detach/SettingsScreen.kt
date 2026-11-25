package com.jhc.detach

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.drawable.toBitmap
import com.jhc.detach.ui.theme.AppTheme
import com.jhc.detach.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where this app is published; Obtainium tracks its GitHub releases. */
private const val REPO_URL = "https://github.com/skofqq/zygisk-detach"
private const val OBTAINIUM_URL = "https://github.com/ImranR98/Obtainium/releases/latest"
private const val MODULE_REPO_URL = "https://github.com/j-hc/zygisk-detach"
private const val ORIGINAL_URL = "https://github.com/j-hc/zygisk-detach-app"

private class SettingsItem(
    val icon: ImageVector,
    @StringRes val title: Int,
    val summary: String,
    val progress: Float? = null,
    val onClick: () -> Unit,
)

internal fun Context.openUri(uri: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onListImported: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val obtainiumMissing = stringResource(R.string.obtainium_missing)

    // export / import of the detach list through the system file picker
    fun showBackupResult(result: Backup.Result) {
        val message = when (result) {
            is Backup.Result.Exported -> context.getString(R.string.backup_exported, result.count)
            is Backup.Result.Imported ->
                if (result.added == 0) context.getString(R.string.backup_imported_nothing)
                else context.getString(R.string.backup_imported, result.added, result.total)
            Backup.Result.EmptyList -> context.getString(R.string.backup_empty)
            Backup.Result.InvalidFile -> context.getString(R.string.backup_invalid)
            is Backup.Result.Failed -> context.getString(R.string.snackbar_error, result.message)
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) scope.launch { showBackupResult(Backup.exportTo(context, uri)) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            val result = Backup.importFrom(context, uri)
            if (result is Backup.Result.Imported && result.added > 0) onListImported()
            showBackupResult(result)
        }
    }
    val backup = listOf(
        SettingsItem(
            ImageVector.vectorResource(R.drawable.ic_upload),
            R.string.backup_export,
            stringResource(R.string.backup_export_summary)
        ) {
            scope.launch {
                if (Backup.hasDetached()) exportLauncher.launch(Backup.suggestedFileName())
                else showBackupResult(Backup.Result.EmptyList)
            }
        },
        SettingsItem(
            ImageVector.vectorResource(R.drawable.ic_download),
            R.string.backup_import,
            stringResource(R.string.backup_import_summary)
        ) { importLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
    )

    val update = Updater.state
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    if (showUpdateDialog && update is UpdateState.Available) {
        UpdateDialog(
            update,
            onInstall = {
                showUpdateDialog = false
                Updater.downloadAndInstall(context)
            },
            onDismiss = { showUpdateDialog = false }
        )
    }
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
                update is UpdateState.Available -> { showUpdateDialog = true }
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
    )
    val about = listOf(
        SettingsItem(
            ImageVector.vectorResource(R.drawable.ic_new_releases),
            R.string.settings_releases,
            stringResource(R.string.settings_releases_summary)
        ) { context.openUri("$REPO_URL/releases") },
        SettingsItem(
            ImageVector.vectorResource(R.drawable.ic_code),
            R.string.settings_source,
            stringResource(R.string.settings_source_summary)
        ) { context.openUri(REPO_URL) },
        SettingsItem(
            ImageVector.vectorResource(R.drawable.ic_extension),
            R.string.settings_module,
            stringResource(R.string.settings_module_summary)
        ) { context.openUri(MODULE_REPO_URL) },
        SettingsItem(
            ImageVector.vectorResource(R.drawable.ic_fork),
            R.string.settings_original,
            stringResource(R.string.settings_original_summary)
        ) { context.openUri(ORIGINAL_URL) },
    )

    // what the hero card says about the module; read once, off the main thread
    var module by remember { mutableStateOf<ModuleStatus?>(null) }
    LaunchedEffect(Unit) {
        module = withContext(Dispatchers.IO) {
            try {
                ModuleStatus.read()
            } catch (_: Exception) {
                null
            }
        }
    }

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
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "hero") {
                HeroCard(
                    version = version,
                    module = module,
                )
            }
            appearanceGroup()
            settingsGroup(R.string.backup_group, backup)
            settingsGroup(R.string.settings_updates, updates)
            settingsGroup(R.string.settings_about, about)
            item(key = "license") {
                Text(
                    stringResource(R.string.settings_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, start = 24.dp, end = 24.dp)
                )
            }
        }
    }
}

/** App identity at the top: icon, version, module state and the one action people come for. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(
    version: String,
    module: ModuleStatus?,
) {
    val context = LocalContext.current
    val icon = remember {
        context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap()
    }
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Image(icon, contentDescription = null, modifier = Modifier.size(60.dp))
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                    Text(
                        stringResource(R.string.settings_version_line, version),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            val (moduleText, moduleOk) = when {
                module == null -> stringResource(R.string.settings_module_checking) to true
                !module.installed -> stringResource(R.string.settings_module_missing) to false
                module.updatePending -> stringResource(R.string.settings_module_reboot) to false
                module.disabled || module.removing || module.zygiskOff ->
                    stringResource(R.string.settings_module_off, module.version.orEmpty()) to false
                else -> stringResource(R.string.settings_module_active, module.version.orEmpty()) to true
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (moduleOk) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                        )
                        Text(
                            moduleText,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.groupTitle(@StringRes title: Int) {
    item(key = "title-$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp)
        )
    }
}

/** Leading icon of a settings row: tinted, in a round tonal container. */
@Composable
private fun RowIcon(icon: ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

private val ThemeMode.icon: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.drawable.ic_contrast
        ThemeMode.LIGHT -> R.drawable.ic_light_mode
        ThemeMode.DARK -> R.drawable.ic_dark_mode
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.appearanceGroup() {
    groupTitle(R.string.settings_appearance)
    val count = if (AppTheme.supportsDynamicColor) 2 else 1
    item(key = "theme") {
        Surface(
            shape = segmentShape(0, count),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RowIcon(ImageVector.vectorResource(R.drawable.ic_palette))
                    Text(
                        stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                // M3 Expressive connected button group: the picked one rounds into a pill
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    val modes = ThemeMode.entries
                    modes.forEachIndexed { index, mode ->
                        ToggleButton(
                            checked = AppTheme.mode == mode,
                            onCheckedChange = { AppTheme.updateMode(mode) },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                            // icon above the label leaves the whole width for long words
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 64.dp)
                                .semantics { role = Role.RadioButton }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painterResource(mode.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                                )
                                Text(
                                    stringResource(mode.label),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
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
                leadingContent = { RowIcon(ImageVector.vectorResource(R.drawable.ic_contrast)) },
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_color_summary)) },
                trailingContent = {
                    Switch(
                        checked = AppTheme.dynamicColor,
                        onCheckedChange = null,
                        thumbContent = if (AppTheme.dynamicColor) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                },
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
                leadingContent = { RowIcon(item.icon) },
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

// Added by skofqq in 2026. Part of a modified version of j-hc/zygisk-detach-app (Apache-2.0).
package com.jhc.detach

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the preset sheet and cards show, for both kinds of presets. */
private class PresetView(
    val key: String,
    val title: String,
    val description: String?,
    val packages: List<String>,
    val builtin: BuiltinPreset? = null,
    val custom: CustomPreset? = null,
    /** Found among the installed root modules. */
    val detected: Boolean = false,
)

@Composable
private fun BuiltinPreset.view() = PresetView(
    key = "builtin-$name",
    title = stringResource(title),
    description = stringResource(description),
    packages = packages,
    builtin = this,
)

/** A preset being edited: [id] is null for a new one. */
private class EditorRequest(val id: String?, val name: String, val packages: List<String>)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PresetsScreen(
    apps: List<DetachedApp>,
    icons: AppIcons,
    /** Apps patched through root modules, found when the list loaded. */
    moduleApps: List<ModuleApp>,
    onBack: () -> Unit,
    /** Apps were marked for detaching; the main screen tells how to apply them. */
    onMarked: (count: Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    var opened by remember { mutableStateOf<PresetView?>(null) }
    // kept after closing, so the editor stays drawn while it slides out
    var editing by remember { mutableStateOf<EditorRequest?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    fun openEditor(request: EditorRequest) {
        editing = request
        editorOpen = true
    }
    var menu by remember { mutableStateOf(false) }

    fun showResult(result: PresetStore.Result) {
        val message = when (result) {
            is PresetStore.Result.Exported -> context.resources.getQuantityString(
                R.plurals.presets_exported, result.count, result.count
            )
            is PresetStore.Result.Imported -> context.resources.getQuantityString(
                R.plurals.presets_imported, result.count, result.count
            )
            PresetStore.Result.Nothing -> context.getString(R.string.presets_nothing_to_export)
            PresetStore.Result.InvalidFile -> context.getString(R.string.presets_invalid)
            is PresetStore.Result.Failed -> context.getString(R.string.snackbar_error, result.message)
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) scope.launch { showResult(PresetStore.exportTo(context, uri)) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            showResult(PresetStore.importFrom(context, uri, context.displayName(uri)))
        }
    }

    val detachedNow = apps.filter { it.detached }.map { it.packageName }
    val customViews = PresetStore.custom.map { preset ->
        PresetView("custom-${preset.id}", preset.name, null, preset.packages, custom = preset)
    }
    val builtinViews = BuiltinPreset.entries.map { it.view() }

    val detectedView = moduleApps.takeIf { it.isNotEmpty() }?.let { found ->
        PresetView(
            key = "detected",
            title = stringResource(R.string.presets_detected),
            description = stringResource(
                R.string.presets_detected_desc,
                found.map { it.moduleName }.distinct().joinToString(", ")
            ),
            packages = found.map { it.packageName },
            detected = true,
        )
    }

    Box {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.presets)) },
                    subtitle = { Text(stringResource(R.string.presets_subtitle)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.presets_export)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_upload), null) },
                                    enabled = PresetStore.custom.isNotEmpty(),
                                    onClick = {
                                        menu = false
                                        exportLauncher.launch(PresetStore.suggestedFileName())
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.presets_import)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_download), null) },
                                    onClick = {
                                        menu = false
                                        importLauncher.launch(
                                            arrayOf("application/json", "text/*", "application/octet-stream")
                                        )
                                    }
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.presets_new)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { openEditor(EditorRequest(null, "", emptyList())) },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    // room for the floating button over the last card
                    bottom = innerPadding.calculateBottomPadding() + 96.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (detectedView != null) {
                    sectionTitle("detected", R.string.presets_detected_section)
                    presetCards(listOf(detectedView), byPackage, icons) { opened = it }
                }
                sectionTitle("mine", R.string.presets_mine)
                if (customViews.isEmpty()) {
                    item(key = "mine-empty") {
                        EmptyMine(
                            canUseDetached = detachedNow.isNotEmpty(),
                            onFromDetached = {
                                openEditor(EditorRequest(null, "", detachedNow))
                            },
                            onPick = { openEditor(EditorRequest(null, "", emptyList())) },
                        )
                    }
                } else {
                    presetCards(customViews, byPackage, icons) { opened = it }
                }
                PresetCategory.entries.forEach { category ->
                    sectionTitle(category.name, category.title)
                    presetCards(
                        builtinViews.filter { it.builtin?.category == category },
                        byPackage, icons
                    ) { opened = it }
                }
                item(key = "footnote") {
                    Text(
                        stringResource(R.string.presets_footnote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp)
                    )
                }
            }
        }

        opened?.let { preset ->
            PresetSheet(
                preset = preset,
                byPackage = byPackage,
                icons = icons,
                onDismiss = { opened = null },
                onMark = { packages ->
                    packages.forEach { byPackage[it]?.detached = true }
                    opened = null
                    onMarked(packages.size)
                },
                onEdit = { custom ->
                    opened = null
                    openEditor(EditorRequest(custom.id, custom.name, custom.packages))
                },
                onCopy = {
                    opened = null
                    openEditor(EditorRequest(null, preset.title, preset.packages))
                },
                onDelete = { custom ->
                    opened = null
                    PresetStore.delete(custom.id)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            context.getString(R.string.presets_deleted, custom.name),
                            actionLabel = context.getString(R.string.action_undo),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) PresetStore.save(custom)
                    }
                },
            )
        }

        // the editor sits over the presets list and closes first on back
        OverlayPage(visible = editorOpen, onBack = { editorOpen = false }) {
            editing?.let { request ->
                PresetEditor(
                    request = request,
                    apps = apps,
                    icons = icons,
                    onCancel = { editorOpen = false },
                    onSave = { name, packages ->
                        if (request.id == null) PresetStore.create(name, packages)
                        else PresetStore.save(CustomPreset(request.id, name, packages))
                        editorOpen = false
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.presets_saved, name))
                        }
                    },
                )
            }
        }
    }
}

private fun Context.displayName(uri: Uri): String? = try {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
} catch (_: Exception) {
    null
}

private fun LazyListScope.sectionTitle(key: String, title: Int) {
    item(key = "title-$key") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp)
        )
    }
}

@Composable
private fun EmptyMine(canUseDetached: Boolean, onFromDetached: () -> Unit, onPick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.presets_mine_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                if (canUseDetached) {
                    FilledTonalButton(onClick = onFromDetached) {
                        Icon(Icons.Filled.Check, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.presets_from_detached))
                    }
                }
                TextButton(onClick = onPick) {
                    Text(stringResource(R.string.presets_pick_apps))
                }
            }
        }
    }
}

private fun installedOf(preset: PresetView, byPackage: Map<String, DetachedApp>) =
    preset.packages.mapNotNull { byPackage[it] }.filter { it.installed }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.presetCards(
    presets: List<PresetView>,
    byPackage: Map<String, DetachedApp>,
    icons: AppIcons,
    onOpen: (PresetView) -> Unit,
) {
    itemsIndexed(presets, key = { _, preset -> preset.key }) { index, preset ->
        val installed = installedOf(preset, byPackage)
        val allDetached = installed.isNotEmpty() && installed.all { it.detached }
        Surface(
            shape = segmentShape(index, presets.size),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .animateItem()
                .clickable { onOpen(preset) }
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { PresetBadge(preset, index) },
                headlineContent = {
                    Text(
                        preset.title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Column {
                        preset.description?.let {
                            Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            IconStack(installed.take(5), icons)
                            Text(
                                when {
                                    installed.isEmpty() -> stringResource(R.string.presets_none_installed)
                                    allDetached -> stringResource(R.string.presets_all_detached)
                                    else -> stringResource(
                                        R.string.presets_installed_of, installed.size, preset.packages.size
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (allDetached) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val badgeShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Pentagon,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PresetBadge(preset: PresetView, index: Int, size: Int = 48) {
    val builtin = preset.builtin
    val region = builtin?.emoji != null
    val shape: Shape = badgeShapes[index % badgeShapes.size].toShape()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(shape)
            .background(
                when {
                    preset.detected -> MaterialTheme.colorScheme.primary
                    builtin == null -> MaterialTheme.colorScheme.tertiaryContainer
                    region -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            )
    ) {
        when {
            preset.detected -> Icon(
                painterResource(R.drawable.ic_extension),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
            builtin?.emoji != null -> Text(builtin.emoji, style = MaterialTheme.typography.titleLarge)
            builtin != null -> Icon(
                painterResource(builtin.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            else -> Text(
                preset.title.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/** Icons of installed apps, overlapping like avatars. */
@Composable
private fun IconStack(apps: List<DetachedApp>, icons: AppIcons) {
    if (apps.isEmpty()) return
    Box(
        Modifier
            .padding(end = 8.dp)
            .width((20 + 14 * (apps.size - 1)).dp)
            .height(20.dp)
    ) {
        apps.forEachIndexed { i, app ->
            Image(
                bitmap = icons.of(app),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = (14 * i).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PresetSheet(
    preset: PresetView,
    byPackage: Map<String, DetachedApp>,
    icons: AppIcons,
    onDismiss: () -> Unit,
    onMark: (List<String>) -> Unit,
    onEdit: (CustomPreset) -> Unit,
    onCopy: () -> Unit,
    onDelete: (CustomPreset) -> Unit,
) {
    // apps that can still be marked are picked by default; the rest are shown for reference
    val markable = preset.packages.filter { byPackage[it]?.let { app -> app.installed && !app.detached } == true }
    val selected = remember(preset.key) { markable.toMutableStateList() }
    val order = preset.packages.sortedBy { pkg ->
        val app = byPackage[pkg]
        when {
            app == null || !app.installed -> 2
            app.detached -> 1
            else -> 0
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                PresetBadge(preset, 0, size = 56)
                Column(
                    Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                ) {
                    Text(preset.title, style = MaterialTheme.typography.headlineSmall)
                    preset.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                val custom = preset.custom
                if (custom != null) {
                    AssistChip(
                        onClick = { onEdit(custom) },
                        label = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)) },
                    )
                    AssistChip(
                        onClick = { onDelete(custom) },
                        label = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)) },
                    )
                } else {
                    AssistChip(
                        onClick = onCopy,
                        label = { Text(stringResource(R.string.presets_copy)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_copy), null, Modifier.size(18.dp)) },
                    )
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 480.dp)
            ) {
                itemsIndexed(order, key = { _, pkg -> pkg }) { index, pkg ->
                    val app = byPackage[pkg]
                    val canMark = pkg in markable
                    PickRow(
                        label = app?.label ?: pkg,
                        packageName = pkg,
                        note = when {
                            app == null || !app.installed -> stringResource(R.string.presets_app_missing)
                            app.detached -> stringResource(R.string.presets_app_detached)
                            else -> null
                        },
                        icon = { AppIconOrPlaceholder(app, icons) },
                        checked = if (canMark) pkg in selected else app?.detached == true,
                        enabled = canMark,
                        shape = segmentShape(index, order.size),
                        onCheckedChange = { if (it) selected += pkg else selected -= pkg },
                    )
                }
            }
            Button(
                onClick = { onMark(selected.toList()) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(56.dp)
            ) {
                Text(
                    if (markable.isEmpty()) stringResource(R.string.presets_nothing_to_mark)
                    else pluralStringResource(R.plurals.presets_mark, selected.size, selected.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun AppIconOrPlaceholder(app: DetachedApp?, icons: AppIcons) {
    Image(
        bitmap = if (app != null) icons.of(app) else icons.uninstalled,
        contentDescription = null,
        modifier = Modifier
            .size(40.dp)
            .alpha(if (app == null || !app.installed) 0.5f else 1f)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PickRow(
    label: String,
    packageName: String,
    note: String?,
    icon: @Composable () -> Unit,
    checked: Boolean,
    enabled: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = shape,
        color = if (checked && enabled) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = icon,
            headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(
                    if (note != null) "$note · $packageName" else packageName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetEditor(
    request: EditorRequest,
    apps: List<DetachedApp>,
    icons: AppIcons,
    onCancel: () -> Unit,
    onSave: (name: String, packages: List<String>) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var name by remember(request) { mutableStateOf(request.name) }
    var query by remember(request) { mutableStateOf("") }
    var onlySelected by remember(request) { mutableStateOf(false) }
    val selected = remember(request) { request.packages.toMutableStateList() }
    // packages typed in by hand or kept from the preset although they're not installed here
    val extra = remember(request) {
        val known = apps.mapTo(HashSet()) { it.packageName }
        mutableStateListOf<String>().apply { addAll(request.packages.filter { it !in known }) }
    }
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    // picked apps first, in the order they had when the editor opened, so rows don't jump on tap
    val ordered = remember(request, apps) {
        val initial = request.packages.toSet()
        apps.filter { it.packageName in initial } + apps.filter { it.packageName !in initial }
    }
    val candidates = extra.toList() + ordered.map { it.packageName }
    val shown = candidates.filter { pkg ->
        val app = byPackage[pkg]
        (!onlySelected || pkg in selected) && (query.isBlank() ||
                pkg.contains(query.trim(), ignoreCase = true) ||
                app?.label?.contains(query.trim(), ignoreCase = true) == true)
    }
    val typed = query.trim()
    val canAddTyped = PACKAGE_NAME.matches(typed) && typed !in candidates
    val canSave = name.isNotBlank() && selected.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (request.id == null) R.string.presets_new else R.string.presets_edit_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    Button(
                        onClick = { onSave(name.trim(), selected.toList()) },
                        enabled = canSave,
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            item(key = "name") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.presets_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }
            item(key = "search") {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.presets_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "summary") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    Text(
                        pluralStringResource(R.plurals.presets_selected, selected.size, selected.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = onlySelected,
                        onClick = { onlySelected = !onlySelected },
                        label = { Text(stringResource(R.string.presets_only_selected)) },
                        shape = CircleShape,
                    )
                }
            }
            if (canAddTyped) {
                item(key = "add-typed") {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable {
                                extra.add(0, typed)
                                selected += typed
                                query = ""
                            }
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                            headlineContent = { Text(stringResource(R.string.presets_add_package, typed)) },
                            supportingContent = { Text(stringResource(R.string.presets_add_package_hint)) },
                        )
                    }
                }
            }
            if (shown.isEmpty() && !canAddTyped) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.no_apps),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            itemsIndexed(shown, key = { _, pkg -> pkg }) { index, pkg ->
                val app = byPackage[pkg]
                PickRow(
                    label = app?.label ?: pkg,
                    packageName = pkg,
                    note = if (app == null || !app.installed) stringResource(R.string.presets_app_missing) else null,
                    icon = { AppIconOrPlaceholder(app, icons) },
                    checked = pkg in selected,
                    enabled = true,
                    shape = segmentShape(index, shown.size),
                    onCheckedChange = { if (it) selected += pkg else selected -= pkg },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

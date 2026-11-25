// Modified by skofqq in 2026: Material 3 Expressive redesign. Original: j-hc/zygisk-detach-app (Apache-2.0).
package com.jhc.detach

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jhc.detach.ui.theme.AppTheme
import com.jhc.detach.ui.theme.ZygiskdetachTheme
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val DETACH_BIN = "$MODULE_DIR/detach"

class DetachedApp(
    val packageName: String,
    val label: String,
    detached: Boolean = false,
    val installed: Boolean = true,
    val system: Boolean = false,
    val updatedSystem: Boolean = false,
    /** Installed only in another profile (work, private space): its icon, already badged. */
    val profileIcon: ImageBitmap? = null,
) {
    var detached by mutableStateOf(detached)

    /** Installed in the work profile or private space (possibly in ours too). */
    var otherProfile = false
}

/** App icons, loaded once and shared by the list and the presets. */
class AppIcons(private val packageManager: PackageManager, val uninstalled: ImageBitmap) {
    private val cache = HashMap<String, ImageBitmap>()

    fun of(app: DetachedApp): ImageBitmap = app.profileIcon ?: cache.getOrPut(app.packageName) {
        if (!app.installed) uninstalled
        else try {
            packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap()
        } catch (_: Exception) {
            uninstalled
        }
    }
}

/** Main sections, switched from the bottom bar. */
private enum class AppSource(@StringRes val label: Int, val icon: ImageVector) {
    ALL(R.string.section_all, Icons.Filled.Home),
    USER(R.string.section_user, Icons.Filled.Person),
    SYSTEM(R.string.section_system, Icons.Filled.Settings);

    fun matches(app: DetachedApp) = when (this) {
        ALL -> true
        USER -> !app.system
        SYSTEM -> app.system
    }
}

/**
 * Extra filters, toggled with chips above the list. Filters combine with AND, so chips of the same
 * group would always give an empty list: picking one drops the other ones of its group.
 */
private enum class AppFilter(
    @StringRes val label: Int,
    val group: Int,
    val matches: (DetachedApp) -> Boolean,
) {
    DETACHED(R.string.filter_detached, 0, { it.detached }),
    NOT_DETACHED(R.string.filter_not_detached, 0, { !it.detached }),
    UPDATED_SYSTEM(R.string.filter_updated_system, 1, { it.updatedSystem }),
    NOT_INSTALLED(R.string.filter_not_installed, 1, { !it.installed }),
    WORK_PROFILE(R.string.filter_work_profile, 1, { it.otherProfile }),
}

private sealed interface LoadState {
    data object Loading : LoadState
    data class Failed(@StringRes val messageRes: Int = 0, val message: String = "") : LoadState
    /** The module's detach binary is missing; [pendingReboot] when it's installed but not active yet. */
    data class NoModule(val pendingReboot: Boolean) : LoadState
    data class Loaded(val apps: List<DetachedApp>, val module: ModuleStatus) : LoadState
}

internal class ShellResult(val ok: Boolean, val out: String, val err: String)

internal fun runShell(cmd: String): ShellResult {
    val op = Shell.cmd(cmd).exec()
    return ShellResult(op.code == 0, op.out.joinToString("\n"), op.err.joinToString("\n"))
}

internal fun String.splitn() =
    if (isEmpty()) emptyList()
    else this.split('\n')

/**
 * Apps installed only in the work profile (or private space) are invisible to getInstalledPackages,
 * which covers just our own user. Root lists them with their APK paths, and the label and icon are
 * read from the APK itself. The detach list is by package name, so one entry covers every profile.
 */
private fun addOtherProfileApps(
    packageManager: PackageManager,
    userManager: UserManager,
    apps: MutableList<DetachedApp>,
) {
    val byName = apps.associateBy { it.packageName }.toMutableMap()
    for (profile in userManager.userProfiles) {
        if (profile == Process.myUserHandle()) continue
        // UserHandle.hashCode() is the user id
        for (line in runShell("pm list packages -f -3 --user ${profile.hashCode()}").out.splitn()) {
            if (!line.startsWith("package:")) continue
            val apk = line.removePrefix("package:").substringBeforeLast('=')
            val packageName = line.substringAfterLast('=')
            val existing = byName[packageName]
            if (existing != null) {
                existing.otherProfile = true
                continue
            }
            val info = packageManager.getPackageArchiveInfo(apk, 0)?.applicationInfo?.apply {
                sourceDir = apk
                publicSourceDir = apk
            }
            val icon = info?.loadIcon(packageManager) ?: packageManager.defaultActivityIcon
            val app = DetachedApp(
                packageName,
                info?.loadLabel(packageManager)?.toString() ?: packageName,
                profileIcon = packageManager.getUserBadgedIcon(icon, profile).toBitmap().asImageBitmap(),
            )
            app.otherProfile = true
            apps += app
            byName[packageName] = app
        }
    }
}

private fun loadApps(packageManager: PackageManager, userManager: UserManager): LoadState {
    if (!Shell.getShell().isRoot) return LoadState.Failed(R.string.error_no_root)
    val module = ModuleStatus.read()
    if (!module.installed) return LoadState.NoModule(pendingReboot = module.updatePending)

    val apps = packageManager.getInstalledPackages(0).mapNotNull {
        it.applicationInfo?.let { info ->
            DetachedApp(
                it.packageName,
                packageManager.getApplicationLabel(info).toString(),
                system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                updatedSystem = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
            )
        }
    }.toMutableList()
    addOtherProfileApps(packageManager, userManager, apps)
    val list = runShell("$DETACH_BIN list")
    val alDetach: List<String> = if (list.ok) {
        list.out.splitn()
    } else {
        // corrupted detach.bin
        runShell("$DETACH_BIN reset")
        listOf()
    }
    for (d in alDetach) {
        if (apps.none { it.packageName == d }) {
            apps.add(DetachedApp(d, d, installed = false))
        }
    }
    apps.forEach { it.detached = alDetach.contains(it.packageName) }
    return LoadState.Loaded(apps.sortedBy { it.label.lowercase() }.sortedBy { !it.detached }, module)
}

/** Shapes of a segmented list: large outer corners, small inner corners. */
internal fun segmentShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppRow(
    app: DetachedApp,
    shape: RoundedCornerShape,
    icon: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = shape,
        color = if (app.detached) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = app.detached,
                role = Role.Switch,
                onValueChange = { app.detached = it }
            )
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                // Expressive shape container: a "cookie" for detached apps, a circle otherwise
                val iconShape = if (app.detached) MaterialShapes.Cookie9Sided.toShape()
                else MaterialShapes.Circle.toShape()
                val rotation by animateFloatAsState(
                    if (app.detached) 40f else 0f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .graphicsLayer { rotationZ = rotation }
                        .clip(iconShape)
                        .background(
                            if (app.detached) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                ) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .graphicsLayer { rotationZ = -rotation }
                    )
                }
            },
            headlineContent = {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                // the work label goes first so a long package name can't push it out of view
                // aligned by baseline: ListItem treats differing first and last baselines as
                // multi-line supporting text and grows to the three-line height
                Row {
                    if (app.otherProfile) {
                        val onlyThere = app.profileIcon != null
                        Surface(
                            shape = CircleShape,
                            color = if (onlyThere) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (onlyThere) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .alignByBaseline()
                                .padding(end = 6.dp)
                        ) {
                            Text(
                                stringResource(
                                    if (onlyThere) R.string.filter_work_profile else R.string.both_profiles
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                    Text(
                        if (app.installed) app.packageName
                        else stringResource(R.string.not_installed, app.packageName),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            },
            trailingContent = {
                Switch(
                    checked = app.detached,
                    onCheckedChange = null,
                    thumbContent = if (app.detached) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    } else null
                )
            }
        )
    }
}

@Composable
private fun FilterChips(selected: Set<AppFilter>, onToggle: (AppFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        AppFilter.entries.forEach { filter ->
            val isSelected = filter in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(filter) },
                label = { Text(stringResource(filter.label)) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
                shape = CircleShape,
            )
        }
    }
}

/**
 * Google Photos style bottom bar: a floating pill with sections, a round search button and, when
 * there is something to apply, the Detach button. Search turns the pill into a field right above
 * the keyboard, where the thumb already is.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BottomBar(
    source: AppSource,
    onSourceChange: (AppSource) -> Unit,
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    pendingChanges: Int,
    onDetach: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            // don't let taps in the gaps around the bar reach the list rows underneath
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        AnimatedContent(
            targetState = searching,
            transitionSpec = {
                (fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.92f))
                    .togetherWith(fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()))
                    .using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f, fill = searching),
            label = "bottom-bar",
        ) { isSearching ->
            if (isSearching) {
                SearchPill(query, onQueryChange, onSearchClose)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionsPill(source, onSourceChange, Modifier.weight(1f, fill = false))
                    Surface(
                        onClick = onSearchOpen,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                        }
                    }
                }
            }
        }
        // only offered when there is something to apply
        AnimatedVisibility(
            visible = pendingChanges > 0,
            enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
            exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut()
        ) {
            BadgedBox(
                badge = { Badge { Text("$pendingChanges") } }
            ) {
                FloatingActionButton(
                    onClick = onDetach,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_detach))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionsPill(source: AppSource, onSourceChange: (AppSource) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(6.dp)
        ) {
            AppSource.entries.forEach { item ->
                val selected = item == source
                val container by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(container)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSourceChange(item) }
                        )
                        .height(52.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    AnimatedVisibility(
                        visible = selected,
                        enter = expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + fadeOut()
                    ) {
                        Row {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                    }
                    Text(
                        stringResource(item.label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** The search field the pill turns into: focused with the keyboard up as soon as it opens. */
@Composable
private fun SearchPill(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1) },
            leadingIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_close_search)
                    )
                }
            },
            trailingIcon = {
                AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    IconButton(onClick = {
                        onQueryChange("")
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .focusRequester(focusRequester)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetachScreen(
    packageManager: PackageManager,
    icons: AppIcons,
    onOpenSettings: () -> Unit,
    /** Bumped when the list changed outside this screen (an import in settings). */
    externalReload: Int = 0,
) {
    val context = LocalContext.current
    val userManager = context.getSystemService(UserManager::class.java)
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(reloadKey, externalReload) {
        state = LoadState.Loading
        state = withContext(Dispatchers.IO) {
            try {
                loadApps(packageManager, userManager)
            } catch (e: Exception) {
                LoadState.Failed(message = e.message ?: e.toString())
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    val listState = rememberLazyListState()
    var source by remember { mutableStateOf(AppSource.ALL) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showPresets by rememberSaveable { mutableStateOf(false) }
    val closeSearch = {
        searching = false
        query = ""
    }
    // back closes the search first (the keyboard, if up, goes away before this)
    BackHandler(enabled = searching, onBack = closeSearch)
    // results start from the top as the query changes
    LaunchedEffect(query) { if (searching) listState.scrollToItem(0) }

    val apps = (state as? LoadState.Loaded)?.apps.orEmpty()
    val module = (state as? LoadState.Loaded)?.module

    // newest module release, checked once per start in the background (cached for a few hours)
    var latestModule by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        latestModule = withContext(Dispatchers.IO) { ModuleRelease.latest(context.applicationContext) }
    }
    var dismissedModule by remember { mutableStateOf(ModuleRelease.dismissed(context)) }
    // turned back on from the notice: it only takes effect after a reboot
    var enabledModule by rememberSaveable { mutableStateOf(false) }
    var confirmReboot by remember { mutableStateOf(false) }
    val notices = buildList {
        if (module == null) return@buildList
        when {
            module.removing -> add(ModuleNotice.Removing)
            module.updatePending || enabledModule -> add(ModuleNotice.Reboot)
            module.disabled -> add(ModuleNotice.Disabled)
            module.zygiskOff -> add(ModuleNotice.ZygiskOff)
        }
        val latest = latestModule
        val current = module.version
        if (latest != null && current != null && !module.updatePending &&
            latest != dismissedModule && isNewerVersion(latest, current)
        ) add(ModuleNotice.Outdated(current.removePrefix("v"), latest))
    }
    val onNoticeAction: (ModuleNotice) -> Unit = { notice ->
        when (notice) {
            ModuleNotice.Disabled -> scope.launch {
                if (withContext(Dispatchers.IO) { ModuleStatus.enable() }) enabledModule = true
            }
            ModuleNotice.Reboot -> confirmReboot = true
            ModuleNotice.Removing -> scope.launch {
                withContext(Dispatchers.IO) { ModuleStatus.keep() }
                reloadKey++
            }
            ModuleNotice.ZygiskOff -> {}
            is ModuleNotice.Outdated -> context.openUri(MODULE_RELEASES)
        }
    }
    val onNoticeDismiss: (ModuleNotice) -> Unit = { notice ->
        if (notice is ModuleNotice.Outdated) {
            ModuleRelease.dismiss(context, notice.latest)
            dismissedModule = notice.latest
        }
    }
    if (confirmReboot) {
        RebootDialog(onConfirm = { confirmReboot = false; ModuleStatus.reboot() }, onDismiss = { confirmReboot = false })
    }
    // what the module actually has detached; pending switch changes don't count until applied
    var applied by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(state) {
        if (state is LoadState.Loaded) {
            applied = apps.filter { it.detached }.map { it.packageName }.toSet()
        }
    }

    Box {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                subtitle = {
                    Text(
                        if (state is LoadState.Loaded) stringResource(R.string.subtitle_detached, applied.size)
                        else stringResource(R.string.subtitle_hint)
                    )
                },
                actions = {
                    IconButton(onClick = { reloadKey++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_reload))
                    }
                    if (state is LoadState.Loaded) IconButton(onClick = { showPresets = true }) {
                        Icon(
                            painterResource(R.drawable.ic_bookmarks),
                            contentDescription = stringResource(R.string.presets)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state is LoadState.Loaded) {
                BottomBar(
                    source = source,
                    onSourceChange = { source = it },
                    searching = searching,
                    query = query,
                    onQueryChange = { query = it },
                    onSearchOpen = { searching = true },
                    onSearchClose = closeSearch,
                    pendingChanges = apps.count { it.detached != (it.packageName in applied) },
                    onDetach = {
                        scope.launch {
                            val detached = apps.filter { it.detached }.map { it.packageName }
                            val result = withContext(Dispatchers.IO) {
                                if (detached.isEmpty()) runShell("$DETACH_BIN reset")
                                else runShell("$DETACH_BIN detachall ${detached.joinToString(" ")}")
                            }
                            if (result.ok) applied = detached.toSet()
                            snackbarHostState.showSnackbar(
                                when {
                                    !result.ok -> resources.getString(R.string.snackbar_error, result.err)
                                    detached.isEmpty() -> resources.getString(R.string.snackbar_emptied)
                                    else -> resources.getString(R.string.snackbar_detached, detached.size)
                                }
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        when (val s = state) {
            // centered on the whole window, where the splash icon was
            LoadState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(modifier = Modifier.size(96.dp))
            }

            is LoadState.NoModule -> NoModuleScreen(
                pendingReboot = s.pendingReboot,
                innerPadding = innerPadding,
                onDownload = { context.openUri(MODULE_RELEASES) },
                onReboot = { confirmReboot = true },
                onRetry = { reloadKey++ },
            )

            is LoadState.Failed -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (s.messageRes != 0) stringResource(s.messageRes) else s.message,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }

            is LoadState.Loaded -> AppsList(
                apps = s.apps,
                listState = listState,
                innerPadding = innerPadding,
                icons = icons,
                // search looks through every app, whatever section was open
                source = if (searching) AppSource.ALL else source,
                query = if (searching) query else "",
                applied = applied,
                notices = notices,
                onNoticeAction = onNoticeAction,
                onNoticeDismiss = onNoticeDismiss,
            )
        }
    }
    OverlayPage(visible = showPresets, onBack = { showPresets = false }) {
        PresetsScreen(
            apps = apps,
            icons = icons,
            onBack = { showPresets = false },
            onMarked = { count ->
                showPresets = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        resources.getQuantityString(R.plurals.snackbar_marked, count, count)
                    )
                }
            },
        )
    }
    }
}

@Composable
private fun AppsList(
    apps: List<DetachedApp>,
    listState: LazyListState,
    innerPadding: PaddingValues,
    icons: AppIcons,
    source: AppSource,
    query: String,
    applied: Set<String>,
    notices: List<ModuleNotice>,
    onNoticeAction: (ModuleNotice) -> Unit,
    onNoticeDismiss: (ModuleNotice) -> Unit,
) {
    var filters by remember { mutableStateOf(emptySet<AppFilter>()) }
    val search = query.trim()

    val shown = apps.filter { app ->
        source.matches(app) && filters.all { it.matches(app) } && (search.isEmpty() ||
                app.packageName.contains(search, ignoreCase = true) ||
                app.label.contains(search, ignoreCase = true))
    }
    // Detached apps always go first, as their own group. Grouping follows the applied state so
    // rows don't jump around while switches are being flipped.
    val (detachedGroup, otherGroup) = shown.partition { it.packageName in applied }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            // the bottom bar floats over the list; keep the last items scrollable above it
            bottom = innerPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "header") {
            Column {
                ModuleNotices(notices, onNoticeAction, onNoticeDismiss)
                FilterChips(filters) { f ->
                    filters = if (f in filters) filters - f
                    else filters.filterTo(HashSet()) { it.group != f.group } + f
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.no_apps),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
        appGroup(R.string.group_detached, detachedGroup, icons)
        appGroup(if (detachedGroup.isEmpty()) 0 else R.string.group_other, otherGroup, icons)
    }
}

// the scrims enableEdgeToEdge() uses by default for 3-button navigation
private val LightScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {
    companion object {
        init {
            Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // ask for root in the background while the splash plays; loadApps() then reuses the shell
        Shell.getShell { }
        AppTheme.init(this)
        PresetStore.init(this)
        Updater.cleanUp(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val uninstalledIcon = getDrawable(R.mipmap.unistalled_app)!!.toBitmap().asImageBitmap()
        setContent {
            // keep status/navigation bar icons readable when the app theme differs from the system one
            val dark = AppTheme.isDark()
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                    ) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { dark }
                )
            }
            ZygiskdetachTheme(darkTheme = dark) {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var listImports by remember { mutableIntStateOf(0) }
                val icons = remember { AppIcons(packageManager, uninstalledIcon) }
                Box {
                    DetachScreen(
                        packageManager,
                        icons,
                        onOpenSettings = { showSettings = true },
                        externalReload = listImports,
                    )
                    OverlayPage(visible = showSettings, onBack = { showSettings = false }) {
                        SettingsScreen(
                            onBack = { showSettings = false },
                            onListImported = { listImports++ },
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.appGroup(
    @StringRes title: Int,
    group: List<DetachedApp>,
    icons: AppIcons,
) {
    if (group.isEmpty()) return
    if (title != 0) {
        item(key = "title-$title") {
            Text(
                "${stringResource(title)} · ${group.size}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 6.dp)
            )
        }
    }
    itemsIndexed(group, key = { _, app -> app.packageName }) { index, app ->
        AppRow(
            app = app,
            shape = segmentShape(index, group.size),
            icon = icons.of(app),
            modifier = Modifier.animateItem()
        )
    }
}

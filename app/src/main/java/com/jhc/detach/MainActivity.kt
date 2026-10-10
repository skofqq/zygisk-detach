// Modified by skofqq in 2026: Material 3 Expressive redesign. Original: j-hc/zygisk-detach-app (Apache-2.0).
package com.jhc.detach

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.activity.SystemBarStyle
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jhc.detach.ui.theme.AppTheme
import com.jhc.detach.ui.theme.ZygiskdetachTheme
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DETACH_BIN = "/data/adb/modules/zygisk-detach/detach"

class DetachedApp(
    val packageName: String,
    val label: String,
    detached: Boolean = false,
    val installed: Boolean = true,
    val system: Boolean = false,
    val updatedSystem: Boolean = false,
) {
    var detached by mutableStateOf(detached)
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

/** Extra filters, toggled with chips above the list. */
private enum class AppFilter(@StringRes val label: Int, val matches: (DetachedApp) -> Boolean) {
    DETACHED(R.string.filter_detached, { it.detached }),
    NOT_DETACHED(R.string.filter_not_detached, { !it.detached }),
    UPDATED_SYSTEM(R.string.filter_updated_system, { it.updatedSystem }),
    NOT_INSTALLED(R.string.filter_not_installed, { !it.installed }),
}

private sealed interface LoadState {
    data object Loading : LoadState
    data class Failed(@StringRes val messageRes: Int = 0, val message: String = "") : LoadState
    data class Loaded(val apps: List<DetachedApp>) : LoadState
}

private class ShellResult(val ok: Boolean, val out: String, val err: String)

private fun runShell(cmd: String): ShellResult {
    val op = Shell.cmd(cmd).exec()
    return ShellResult(op.code == 0, op.out.joinToString("\n"), op.err.joinToString("\n"))
}

private fun String.splitn() =
    if (isEmpty()) emptyList()
    else this.split('\n')

private fun loadApps(packageManager: PackageManager): LoadState {
    if (!Shell.getShell().isRoot) return LoadState.Failed(R.string.error_no_root)
    if (!runShell("test -f $DETACH_BIN").ok) {
        return LoadState.Failed(R.string.error_no_module)
    }

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
    return LoadState.Loaded(apps.sortedBy { it.label.lowercase() }.sortedBy { !it.detached })
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
                Text(
                    if (app.installed) app.packageName
                    else stringResource(R.string.not_installed, app.packageName),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

/** Google Photos style bottom bar: a floating pill with sections plus a separate round action. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BottomBar(
    source: AppSource,
    onSourceChange: (AppSource) -> Unit,
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
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp,
            modifier = Modifier.weight(1f, fill = false)
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

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = {
                    onQueryChange("")
                    focusManager.clearFocus()
                }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetachScreen(
    packageManager: PackageManager,
    uninstalledIcon: ImageBitmap,
    onOpenSettings: () -> Unit,
) {
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(reloadKey) {
        state = LoadState.Loading
        state = withContext(Dispatchers.IO) {
            try {
                loadApps(packageManager)
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

    val apps = (state as? LoadState.Loaded)?.apps.orEmpty()
    // what the module actually has detached; pending switch changes don't count until applied
    var applied by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(state) {
        if (state is LoadState.Loaded) {
            applied = apps.filter { it.detached }.map { it.packageName }.toSet()
        }
    }

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
                packageManager = packageManager,
                uninstalledIcon = uninstalledIcon,
                source = source,
                applied = applied,
            )
        }
    }
}

@Composable
private fun AppsList(
    apps: List<DetachedApp>,
    listState: LazyListState,
    innerPadding: PaddingValues,
    packageManager: PackageManager,
    uninstalledIcon: ImageBitmap,
    source: AppSource,
    applied: Set<String>,
) {
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(emptySet<AppFilter>()) }
    val iconCache = remember { HashMap<String, ImageBitmap>() }

    val shown = apps.filter { app ->
        source.matches(app) && filters.all { it.matches(app) } && (query.isEmpty() ||
                app.packageName.contains(query, ignoreCase = true) ||
                app.label.contains(query, ignoreCase = true))
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
                SearchField(query) { query = it }
                Spacer(Modifier.height(8.dp))
                FilterChips(filters) { f ->
                    filters = if (f in filters) filters - f else filters + f
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
        appGroup(R.string.group_detached, detachedGroup, iconCache, packageManager, uninstalledIcon)
        appGroup(
            if (detachedGroup.isEmpty()) 0 else R.string.group_other,
            otherGroup, iconCache, packageManager, uninstalledIcon
        )
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
                // predictive back: the settings page shrinks toward the swipe and reveals the list
                val backProgress = remember { Animatable(0f) }
                var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
                LaunchedEffect(showSettings) { if (showSettings) backProgress.snapTo(0f) }
                PredictiveBackHandler(enabled = showSettings) { events ->
                    try {
                        events.collect { event ->
                            swipeEdge = event.swipeEdge
                            backProgress.snapTo(event.progress)
                        }
                        showSettings = false
                    } catch (_: CancellationException) {
                        backProgress.animateTo(0f)
                    }
                }
                // settings slide over the main screen so its state (pending switches) survives
                Box {
                    DetachScreen(packageManager, uninstalledIcon, onOpenSettings = { showSettings = true })
                    AnimatedVisibility(
                        visible = showSettings,
                        enter = slideInHorizontally { it } + fadeIn(),
                        exit = slideOutHorizontally { it } + fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.graphicsLayer {
                                val progress = backProgress.value
                                val scale = 1f - 0.1f * progress
                                scaleX = scale
                                scaleY = scale
                                // drift away from the edge the swipe started at, by up to 8% of the width
                                val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1
                                translationX = direction * progress * size.width * 0.08f
                                shape = RoundedCornerShape(32.dp * progress)
                                clip = progress > 0f
                            }
                        ) {
                            SettingsScreen(onBack = { showSettings = false })
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.appGroup(
    @StringRes title: Int,
    group: List<DetachedApp>,
    iconCache: HashMap<String, ImageBitmap>,
    packageManager: PackageManager,
    uninstalledIcon: ImageBitmap,
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
        val icon = iconCache.getOrPut(app.packageName) {
            if (!app.installed) uninstalledIcon
            else try {
                packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap()
            } catch (_: Exception) {
                uninstalledIcon
            }
        }
        AppRow(
            app = app,
            shape = segmentShape(index, group.size),
            icon = icon,
            modifier = Modifier.animateItem()
        )
    }
}

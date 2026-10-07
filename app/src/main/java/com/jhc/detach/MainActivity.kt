package com.jhc.detach

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.jhc.detach.ui.theme.ZygiskdetachTheme
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DETACH_BIN = "/data/adb/modules/zygisk-detach/detach"

class DetachedApp(
    val packageName: String,
    val label: String,
    detached: Boolean = false,
    val installed: Boolean = true
) {
    var detached by mutableStateOf(detached)
}

private sealed interface LoadState {
    data object Loading : LoadState
    data class Failed(val message: String) : LoadState
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
    Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
    if (!Shell.getShell().isRoot) return LoadState.Failed("Root access is required")
    if (!runShell("test -f $DETACH_BIN").ok) {
        return LoadState.Failed("The zygisk-detach module is not installed")
    }

    val apps = packageManager.getInstalledPackages(0).mapNotNull {
        it.applicationInfo?.let { info ->
            DetachedApp(it.packageName, packageManager.getApplicationLabel(info).toString())
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
private fun segmentShape(index: Int, count: Int): RoundedCornerShape {
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
) {
    Surface(
        shape = shape,
        color = if (app.detached) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
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
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
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
                    if (app.installed) app.packageName else "${app.packageName} · not installed",
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterButtons(onlyDetached: Boolean, detachedCount: Int, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        ToggleButton(
            checked = !onlyDetached,
            onCheckedChange = { onChange(false) },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            modifier = Modifier.weight(1f)
        ) {
            Text("All apps")
        }
        ToggleButton(
            checked = onlyDetached,
            onCheckedChange = { onChange(true) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            modifier = Modifier.weight(1f)
        ) {
            Text("Detached ($detachedCount)")
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = {
                    onQueryChange("")
                    focusManager.clearFocus()
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear")
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
private fun DetachScreen(packageManager: PackageManager, uninstalledIcon: ImageBitmap) {
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    LaunchedEffect(Unit) {
        state = withContext(Dispatchers.IO) {
            try {
                loadApps(packageManager)
            } catch (e: Exception) {
                LoadState.Failed(e.message ?: e.toString())
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { !listState.isScrollInProgress } }

    val apps = (state as? LoadState.Loaded)?.apps.orEmpty()
    val detachedCount = apps.count { it.detached }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("zygisk-detach") },
                subtitle = {
                    Text(
                        if (state is LoadState.Loaded) "$detachedCount detached from Play Store"
                        else "Detach apps from Play Store updates"
                    )
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state is LoadState.Loaded) {
                MediumExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            val detached = apps.filter { it.detached }.map { it.packageName }
                            val result = withContext(Dispatchers.IO) {
                                if (detached.isEmpty()) runShell("$DETACH_BIN reset")
                                else runShell("$DETACH_BIN detachall ${detached.joinToString(" ")}")
                            }
                            snackbarHostState.showSnackbar(
                                when {
                                    !result.ok -> "Error: ${result.err}"
                                    detached.isEmpty() -> "Emptied the detach list"
                                    else -> "Detached ${detached.size} apps"
                                }
                            )
                        }
                    },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    text = { Text("Detach") }
                )
            }
        }
    ) { innerPadding ->
        when (val s = state) {
            LoadState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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
                    s.message,
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
                detachedCount = detachedCount
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
    detachedCount: Int,
) {
    var query by remember { mutableStateOf("") }
    var onlyDetached by remember { mutableStateOf(false) }
    val iconCache = remember { HashMap<String, ImageBitmap>() }

    val shown = apps.filter { app ->
        (!onlyDetached || app.detached) && (query.isEmpty() ||
                app.packageName.contains(query, ignoreCase = true) ||
                app.label.contains(query, ignoreCase = true))
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            // keep the last items scrollable above the gesture bar and the Detach button
            bottom = innerPadding.calculateBottomPadding() + 104.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "header") {
            Column {
                SearchField(query) { query = it }
                Spacer(Modifier.height(12.dp))
                FilterButtons(onlyDetached, detachedCount) { onlyDetached = it }
                Spacer(Modifier.height(14.dp))
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No apps found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
        itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
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
                shape = segmentShape(index, shown.size),
                icon = icon,
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val uninstalledIcon = getDrawable(R.mipmap.unistalled_app)!!.toBitmap().asImageBitmap()
        setContent {
            ZygiskdetachTheme {
                DetachScreen(packageManager, uninstalledIcon)
            }
        }
    }
}

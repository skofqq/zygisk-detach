// Added by skofqq in 2026. Part of a modified version of j-hc/zygisk-detach-app (Apache-2.0).
package com.jhc.detach

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: String, val apkUrl: String, val size: Long) : UpdateState
    data class Downloading(val version: String, val progress: Float) : UpdateState
    data class Installing(val version: String) : UpdateState
    data class Failed(val message: String, val signatureMismatch: Boolean = false) : UpdateState
}

/**
 * Checks GitHub releases of this app, downloads the APK and installs it through the root shell,
 * so no installer prompt is needed. The APK is deleted once pm is done with it.
 * Lives outside the settings screen so a download keeps going when the screen is closed.
 */
object Updater {
    private const val REPO_URL = "https://github.com/skofqq/zygisk-detach"
    private const val LATEST_RELEASE =
        "https://api.github.com/repos/skofqq/zygisk-detach/releases/latest"
    private const val INSTALL_APK = "/data/local/tmp/zygisk-detach-update.apk"
    private const val INSTALL_LOG = "/data/local/tmp/zygisk-detach-update.log"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    private fun apkFile(context: Context) = File(context.cacheDir, "update.apk")

    /** Removes an APK left behind by an update that was interrupted. */
    fun cleanUp(context: Context) {
        if (state is UpdateState.Downloading || state is UpdateState.Installing) return
        apkFile(context).delete()
    }

    fun check(currentVersion: String) {
        if (job?.isActive == true) return
        job = scope.launch {
            state = UpdateState.Checking
            state = try {
                val (version, apkUrl, size) = latestRelease()
                if (isNewer(version, currentVersion)) UpdateState.Available(version, apkUrl, size)
                else UpdateState.UpToDate
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun downloadAndInstall(context: Context) {
        val update = state as? UpdateState.Available ?: return
        if (job?.isActive == true) return
        val apk = apkFile(context.applicationContext)
        job = scope.launch {
            try {
                state = UpdateState.Downloading(update.version, 0f)
                download(update.apkUrl, apk, update.size) {
                    state = UpdateState.Downloading(update.version, it)
                }
                state = UpdateState.Installing(update.version)
                install(apk, context.packageName)
            } catch (e: Exception) {
                apk.delete()
                state = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private data class Release(val version: String, val apkUrl: String, val size: Long)

    private fun latestRelease(): Release = try {
        val release = JSONObject(get(LATEST_RELEASE))
        val version = release.getString("tag_name").removePrefix("v")
        val assets = release.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?: error("no APK in release v$version")
        Release(version, apk.getString("browser_download_url"), apk.optLong("size"))
    } catch (_: RateLimited) {
        latestReleaseFromRedirect()
    }

    /**
     * The API allows 60 requests an hour per IP, shared with Obtainium and anything else on the
     * network. /releases/latest on the website has no such limit: it redirects to the tag page,
     * and CI names the APK after the tag.
     */
    private fun latestReleaseFromRedirect(): Release {
        val conn = URL("$REPO_URL/releases/latest").openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "zygisk-detach-app")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            val location = conn.getHeaderField("Location")
            val tag = location?.substringAfter("/releases/tag/", "")?.takeIf { it.isNotEmpty() }
                ?: error("GitHub HTTP ${conn.responseCode}")
            return Release(
                tag.removePrefix("v"),
                "$REPO_URL/releases/download/$tag/zygisk-detach-app-$tag.apk",
                0
            )
        } finally {
            conn.disconnect()
        }
    }

    private class RateLimited : Exception()

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "zygisk-detach-app")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {}
                HttpURLConnection.HTTP_FORBIDDEN, 429 -> throw RateLimited()
                else -> error("GitHub HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun download(url: String, target: File, expectedSize: Long, onProgress: (Float) -> Unit) {
        // GitHub redirects asset downloads to its CDN (https -> https, followed automatically)
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("User-Agent", "zygisk-detach-app")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                error("download HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            var done = 0L
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun install(apk: File, packageName: String) {
        if (!Shell.getShell().isRoot) error("no root access")
        // pm kills this app when it replaces it, so the install runs detached from our process;
        // it always deletes the APK; on success it also deletes the log and relaunches the app
        // (copied to /data/local/tmp, which system_server may read, unlike our app data)
        val script = "pm install -r $INSTALL_APK > $INSTALL_LOG 2>&1; rm -f $INSTALL_APK; " +
            "grep -q Success $INSTALL_LOG && { rm -f $INSTALL_LOG; am start -n $packageName/.MainActivity; }"
        val result = Shell.cmd(
            "rm -f $INSTALL_LOG",
            "cp \"${apk.absolutePath}\" $INSTALL_APK && chmod 644 $INSTALL_APK",
        ).exec()
        apk.delete()
        if (!result.isSuccess) error(result.err.joinToString("\n").ifEmpty { "cannot copy APK" })
        Shell.cmd("setsid sh -c '$script' < /dev/null > /dev/null 2>&1 &").exec()

        // still alive: either pm is busy or it failed, so wait for its verdict
        repeat(240) {
            delay(500)
            val log = Shell.cmd("cat $INSTALL_LOG 2>/dev/null").exec().out.joinToString("\n")
            if (log.contains("Failure")) {
                Shell.cmd("rm -f $INSTALL_LOG").exec()
                state = UpdateState.Failed(
                    log.substringAfter("Failure").trim().trim('[', ']'),
                    signatureMismatch = "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in log
                )
                return
            }
        }
        state = UpdateState.Failed("pm install timed out")
    }

    /** Compares dotted version numbers, e.g. "1.10" is newer than "1.9". */
    private fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

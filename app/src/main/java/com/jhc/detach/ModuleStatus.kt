// Added by skofqq in 2026. Part of a modified version of j-hc/zygisk-detach-app (Apache-2.0).
package com.jhc.detach

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.net.HttpURLConnection
import java.net.URL

const val MODULE_ID = "zygisk-detach"
const val MODULE_DIR = "/data/adb/modules/$MODULE_ID"
const val MODULE_RELEASES = "https://github.com/j-hc/zygisk-detach/releases/latest"

/** What the root shell sees of the zygisk-detach module. */
data class ModuleStatus(
    /** The detach binary is in place, so the detach list can be read and written. */
    val installed: Boolean,
    /** "v1.24.0" from module.prop, if any. */
    val version: String?,
    /** Turned off in the root manager: Play Store is not patched until it is turned back on. */
    val disabled: Boolean,
    /** Marked for removal in the root manager. */
    val removing: Boolean,
    /** Installed or updated in the root manager, but the device hasn't rebooted since. */
    val updatePending: Boolean,
    /** Magisk reports Zygisk off and no other Zygisk provider module is active. */
    val zygiskOff: Boolean,
) {
    companion object {
        /** Reads everything in one root call, so app start doesn't wait on several. */
        fun read(): ModuleStatus {
            val script = """
                M=$MODULE_DIR
                [ -f ${'$'}M/detach ] && echo has:bin
                [ -f ${'$'}M/disable ] && echo has:disable
                [ -f ${'$'}M/remove ] && echo has:remove
                [ -d /data/adb/modules_update/$MODULE_ID ] && echo has:update
                sed -n 's/^version=/prop:version=/p' ${'$'}M/module.prop 2>/dev/null
                if command -v magisk >/dev/null 2>&1; then
                  magisk --sqlite "SELECT value FROM settings WHERE key='zygisk'" 2>/dev/null | sed 's/^/zygisk:/'
                fi
                for d in /data/adb/modules/*; do
                  case "${'$'}{d##*/}" in ($MODULE_ID) continue;; esac
                  case "${'$'}{d##*/}" in (*[Zz]ygisk*) [ -f "${'$'}d/disable" ] || echo has:provider;; esac
                done
            """.trimIndent()
            val lines = Shell.cmd(script).exec().out
            fun has(flag: String) = "has:$flag" in lines
            val zygiskSetting = lines.firstOrNull { it.startsWith("zygisk:") }
            return ModuleStatus(
                installed = has("bin"),
                version = lines.firstOrNull { it.startsWith("prop:version=") }
                    ?.substringAfter('=')?.trim()?.takeIf { it.isNotEmpty() },
                disabled = has("disable"),
                removing = has("remove"),
                updatePending = has("update"),
                // Magisk prints "value=0"; without Magisk (KernelSU, APatch) nothing is printed
                zygiskOff = zygiskSetting?.substringAfter("value=")?.trim() == "0" && !has("provider"),
            )
        }

        fun enable(): Boolean = Shell.cmd("rm -f $MODULE_DIR/disable").exec().isSuccess

        /** Takes back a removal marked in the root manager. */
        fun keep(): Boolean = Shell.cmd("rm -f $MODULE_DIR/remove").exec().isSuccess

        fun reboot() {
            Shell.cmd("svc power reboot || reboot").submit()
        }
    }
}

/**
 * Newest module release on GitHub, "1.24.0". The website's /releases/latest redirect is used rather
 * than the API: it has no rate limit and only the tag is needed. Cached for a few hours so every
 * app start doesn't go to the network.
 */
object ModuleRelease {
    private const val CACHE_MS = 6 * 60 * 60 * 1000L

    fun latest(context: Context): String? {
        val prefs = context.getSharedPreferences("module", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val cached = prefs.getString("latest", null)
        if (cached != null && now - prefs.getLong("checked", 0) in 0 until CACHE_MS) return cached
        val fresh = try {
            fetchLatestTag()
        } catch (_: Exception) {
            null
        } ?: return cached
        prefs.edit().putString("latest", fresh).putLong("checked", now).apply()
        return fresh
    }

    private fun fetchLatestTag(): String? {
        val conn = URL(MODULE_RELEASES).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "zygisk-detach-app")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            return conn.getHeaderField("Location")
                ?.substringAfter("/releases/tag/", "")
                ?.takeIf { it.isNotEmpty() }
                ?.removePrefix("v")
        } finally {
            conn.disconnect()
        }
    }

    /** The release the user chose to stop hearing about. */
    fun dismissed(context: Context): String? =
        context.getSharedPreferences("module", Context.MODE_PRIVATE).getString("dismissed", null)

    fun dismiss(context: Context, version: String) {
        context.getSharedPreferences("module", Context.MODE_PRIVATE).edit()
            .putString("dismissed", version).apply()
    }
}

/** Compares dotted version numbers, e.g. "1.10" is newer than "1.9". A leading "v" is ignored. */
fun isNewerVersion(candidate: String, current: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").split('.', '-', ' ').map { it.toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

// Added by skofqq in 2026. Part of a modified version of j-hc/zygisk-detach-app (Apache-2.0).
package com.jhc.detach

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Package names as the detach binary accepts them. */
internal val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

internal enum class PresetCategory(@StringRes val title: Int) {
    PATCHED(R.string.preset_category_patched),
    MORPHE(R.string.preset_category_morphe),
    REGION(R.string.preset_category_region),
}

/**
 * A ready-made list shipped with the app: apps that other developers often patch or mod, so the
 * Play Store must not overwrite them, and region builds that Play would replace with global ones.
 */
internal enum class BuiltinPreset(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val category: PresetCategory,
    @DrawableRes val icon: Int = 0,
    /** A flag instead of an icon, for regional presets. */
    val emoji: String? = null,
    /** The icon is a full-color logo: shown as is, on a white circle. */
    val brandColors: Boolean = false,
    val packages: List<String>,
) {
    YOUTUBE(
        R.string.preset_youtube, R.string.preset_youtube_desc, PresetCategory.PATCHED,
        icon = R.drawable.ic_logo_youtube,
        packages = listOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
        ),
    ),
    SOCIAL(
        R.string.preset_social, R.string.preset_social_desc, PresetCategory.PATCHED,
        icon = R.drawable.ic_forum,
        packages = listOf(
            "com.reddit.frontpage",
            "com.twitter.android",
            "com.instagram.android",
            "com.instagram.barcelona",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.facebook.katana",
            "com.facebook.orca",
            "com.tumblr",
            "com.pinterest",
            "com.snapchat.android",
        ),
    ),
    MUSIC(
        R.string.preset_music, R.string.preset_music_desc, PresetCategory.PATCHED,
        icon = R.drawable.ic_logo_spotify,
        packages = listOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.soundcloud.android",
            "deezer.android.app",
        ),
    ),
    GOOGLE(
        R.string.preset_google, R.string.preset_google_desc, PresetCategory.PATCHED,
        icon = R.drawable.ic_logo_google,
        brandColors = true,
        packages = listOf(
            "com.google.android.apps.photos",
            "com.google.android.apps.magazines",
            "com.google.android.apps.recorder",
        ),
    ),
    OTHER(
        R.string.preset_other, R.string.preset_other_desc, PresetCategory.PATCHED,
        icon = R.drawable.ic_extension,
        packages = listOf(
            "tv.twitch.android.app",
            "com.strava",
            "com.duolingo",
            "com.adobe.lrmobile",
            "net.dinglisch.android.taskerm",
        ),
    ),
    // compatible packages from the patches-list.json of MorpheApp/morphe-patches, crimera/piko
    // and RookieEnough/De-Vanced (Morphe patch bundles)
    MORPHE_SOCIAL(
        R.string.preset_morphe_social, R.string.preset_morphe_social_desc, PresetCategory.MORPHE,
        icon = R.drawable.ic_forum,
        packages = listOf(
            "com.reddit.frontpage",
            "com.twitter.android",
            "com.instagram.android",
            "com.facebook.katana",
            "com.facebook.orca",
            "com.viber.voip",
            "jp.pxv.android",
            "com.letterboxd.letterboxd",
        ),
    ),
    MORPHE_MEDIA(
        R.string.preset_morphe_media, R.string.preset_morphe_media_desc, PresetCategory.MORPHE,
        icon = R.drawable.ic_music_note,
        packages = listOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.amazon.mp3",
            "com.bandcamp.android",
            "com.disney.disneyplus",
            "com.peacocktv.peacockandroid",
        ),
    ),
    MORPHE_TOOLS(
        R.string.preset_morphe_tools, R.string.preset_morphe_tools_desc, PresetCategory.MORPHE,
        icon = R.drawable.ic_apps,
        packages = listOf(
            "com.google.android.apps.photos",
            "com.google.android.apps.recorder",
            "com.microsoft.office.officelens",
            "com.microblink.photomath",
            "com.adobe.photoshopmix",
            "ginlemon.iconpackstudio",
            "com.myprog.hexedit",
            "net.binarymode.android.irplus",
            "com.drinkplusplus.angulus",
            "com.nothing.smartcenter",
        ),
    ),
    MORPHE_OTHER(
        R.string.preset_morphe_other, R.string.preset_morphe_other_desc, PresetCategory.MORPHE,
        icon = R.drawable.ic_extension,
        packages = listOf(
            "com.strava",
            "com.cricbuzz.android",
            "nl.sanomamedia.android.nu",
            "de.gmx.mobile.android.mail",
            "com.amazon.mShop.android.shopping",
        ),
    ),
    RU_BANKS(
        R.string.preset_ru_banks, R.string.preset_ru_banks_desc, PresetCategory.REGION,
        emoji = "🇷🇺",
        packages = listOf(
            "ru.sberbankmobile",
            "com.idamob.tinkoff.android",
            "ru.vtb24.mobilebanking.android",
            "ru.alfabank.mobile.android",
        ),
    ),
    RU_SERVICES(
        R.string.preset_ru_services, R.string.preset_ru_services_desc, PresetCategory.REGION,
        emoji = "🇷🇺",
        packages = listOf(
            "ru.yandex.music",
            "com.vkontakte.android",
            "com.uma.musicvk",
            "ru.kinopoisk",
        ),
    ),
    CHINA(
        R.string.preset_china, R.string.preset_china_desc, PresetCategory.REGION,
        emoji = "🇨🇳",
        packages = listOf(
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.eg.android.AlipayGphone",
            "tv.danmaku.bili",
            "com.ss.android.ugc.aweme",
        ),
    ),
}

/** An app that a root module replaces with its own (patched) APK. */
internal class ModuleApp(val packageName: String, val moduleName: String)

/**
 * Finds apps patched through root modules (ReVanced, Morphe, RVX, revanced-magisk-module and the
 * like). Such a module ships an APK and bind-mounts it over the installed app, so its scripts or
 * config name the package. Heuristic: only active modules that carry an .apk file are looked at,
 * and only package names of apps installed here count.
 */
internal object ModuleScan {
    /** Packages that module scripts mention for other reasons (Play Store, GMS, microG). */
    private val IGNORED = setOf(
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "app.revanced.android.gms",
        "com.mgoogle.android.gms",
        "app.morphe.android.gms",
        "com.android.shell",
    )

    fun scan(installed: Set<String>): List<ModuleApp> {
        val script = """
            for d in /data/adb/modules/*; do
              [ -d "${'$'}d" ] || continue
              id=${'$'}{d##*/}
              [ "${'$'}id" = "$MODULE_ID" ] && continue
              [ -f "${'$'}d/disable" ] || [ -f "${'$'}d/remove" ] && continue
              [ -n "${'$'}(find "${'$'}d" -maxdepth 3 -name '*.apk' 2>/dev/null | head -n 1)" ] || continue
              name=${'$'}(sed -n 's/^name=//p' "${'$'}d/module.prop" 2>/dev/null | head -n 1)
              for f in module.prop config service.sh post-fs-data.sh customize.sh uninstall.sh; do
                [ -f "${'$'}d/${'$'}f" ] && head -c 65536 "${'$'}d/${'$'}f"
                echo
              done | grep -oE '[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+' | sort -u | while read -r p; do
                echo "pkg|${'$'}{name:-${'$'}id}|${'$'}p"
              done
            done
        """.trimIndent()
        val found = LinkedHashMap<String, ModuleApp>()
        for (line in runShell(script).out.splitn()) {
            val parts = line.split('|')
            if (parts.size != 3 || parts[0] != "pkg") continue
            val pkg = parts[2]
            if (pkg in IGNORED || pkg !in installed || pkg in found) continue
            found[pkg] = ModuleApp(pkg, parts[1].trim())
        }
        return found.values.toList()
    }
}

/** A preset made by the user. */
internal data class CustomPreset(val id: String, val name: String, val packages: List<String>)

/** The user's own presets, kept in SharedPreferences as JSON and observed by Compose. */
internal object PresetStore {
    private const val KEY = "custom"
    private lateinit var prefs: SharedPreferences

    val custom = mutableStateListOf<CustomPreset>()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("presets", Context.MODE_PRIVATE)
        custom.clear()
        custom += parse(prefs.getString(KEY, null).orEmpty()).orEmpty()
    }

    fun save(preset: CustomPreset) {
        val index = custom.indexOfFirst { it.id == preset.id }
        if (index >= 0) custom[index] = preset else custom += preset
        persist()
    }

    fun create(name: String, packages: List<String>): CustomPreset =
        CustomPreset(UUID.randomUUID().toString(), name, packages.distinct()).also { save(it) }

    fun delete(id: String) {
        custom.removeAll { it.id == id }
        persist()
    }

    private fun persist() {
        prefs.edit().putString(KEY, serialize(custom)).apply()
    }

    fun serialize(presets: List<CustomPreset>): String = JSONObject()
        .put("format", "zygisk-detach-presets")
        .put("version", 1)
        .put("presets", JSONArray().apply {
            presets.forEach { preset ->
                put(JSONObject()
                    .put("name", preset.name)
                    .put("packages", JSONArray(preset.packages)))
            }
        })
        .toString(2)

    /** Null when the text is not a presets file. */
    fun parse(text: String): List<CustomPreset>? {
        if (text.isBlank()) return emptyList()
        return try {
            val array = JSONObject(text).getJSONArray("presets")
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                val packages = item.getJSONArray("packages")
                CustomPreset(
                    id = item.optString("id").ifEmpty { UUID.randomUUID().toString() },
                    name = item.getString("name").trim(),
                    packages = (0 until packages.length()).map { packages.getString(it).trim() }
                        .filter { PACKAGE_NAME.matches(it) }
                        .distinct(),
                )
            }.filter { it.name.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    sealed interface Result {
        data class Exported(val count: Int) : Result
        data class Imported(val count: Int) : Result
        data object Nothing : Result
        data object InvalidFile : Result
        data class Failed(val message: String) : Result
    }

    fun suggestedFileName() = "zygisk-detach-presets.json"

    suspend fun exportTo(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            val presets = custom.toList()
            if (presets.isEmpty()) return@withContext Result.Nothing
            context.contentResolver.openOutputStream(uri, "wt")!!.use {
                it.write(serialize(presets).toByteArray())
            }
            Result.Exported(presets.size)
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.toString())
        }
    }

    /**
     * Takes a presets file, or a plain detach list exported from the settings, which becomes one
     * preset named after the file. Presets with a name that already exists replace that one.
     */
    suspend fun importFrom(context: Context, uri: Uri, fileName: String?): Result = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)!!.use {
                val bytes = it.readBytes()
                if (bytes.size > 1024 * 1024) return@withContext Result.InvalidFile
                String(bytes)
            }
            val imported = if (text.trimStart().startsWith("{")) {
                parse(text) ?: return@withContext Result.InvalidFile
            } else {
                val packages = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                if (packages.isEmpty() || packages.any { !PACKAGE_NAME.matches(it) }) {
                    return@withContext Result.InvalidFile
                }
                val name = fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.presets_imported_name)
                listOf(CustomPreset(UUID.randomUUID().toString(), name, packages.distinct()))
            }
            if (imported.isEmpty()) return@withContext Result.Nothing
            withContext(Dispatchers.Main) {
                imported.forEach { preset ->
                    val same = custom.firstOrNull { it.name.equals(preset.name, ignoreCase = true) }
                    save(if (same != null) preset.copy(id = same.id) else preset)
                }
            }
            Result.Imported(imported.size)
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.toString())
        }
    }
}

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
    val packages: List<String>,
) {
    YOUTUBE(
        R.string.preset_youtube, R.string.preset_youtube_desc, PresetCategory.PATCHED,
        icon = R.drawable.ic_play_circle,
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
        icon = R.drawable.ic_music_note,
        packages = listOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.soundcloud.android",
            "deezer.android.app",
        ),
    ),
    GOOGLE(
        R.string.preset_google, R.string.preset_google_desc, PresetCategory.PATCHED,
        icon = R.drawable.ic_apps,
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

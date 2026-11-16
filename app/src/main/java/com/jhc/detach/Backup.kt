package com.jhc.detach

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Export and import of the detach list, so it can move to another device or be restored after a
 * reflash. The file is plain text: one package name per line, lines starting with # are comments.
 */
internal object Backup {
    private const val HEADER = "# Zygisk Detach list: one package name per line"
    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    sealed interface Result {
        data class Exported(val count: Int) : Result
        data class Imported(val added: Int, val total: Int) : Result
        data object EmptyList : Result
        data object InvalidFile : Result
        data class Failed(val message: String) : Result
    }

    fun suggestedFileName() = "zygisk-detach-list.txt"

    /** Checked before the file picker opens, so an empty list doesn't leave an empty file behind. */
    suspend fun hasDetached(): Boolean = withContext(Dispatchers.IO) { !detachedNow().isNullOrEmpty() }

    private fun detachedNow(): List<String>? {
        if (!runShell("test -f $DETACH_BIN").ok) return null
        val list = runShell("$DETACH_BIN list")
        return if (list.ok) list.out.splitn().map { it.trim() }.filter { it.isNotEmpty() } else null
    }

    suspend fun exportTo(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            val packages = detachedNow() ?: return@withContext Result.Failed(
                context.getString(R.string.error_no_module)
            )
            if (packages.isEmpty()) return@withContext Result.EmptyList
            val text = (listOf(HEADER) + packages.sorted()).joinToString("\n", postfix = "\n")
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            Result.Exported(packages.size)
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.toString())
        }
    }

    /** Adds the packages from the file to the current list; nothing already detached is removed. */
    suspend fun importFrom(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            val lines = context.contentResolver.openInputStream(uri)!!.use {
                // a detach list is tiny; anything bigger is not one
                val bytes = it.readNBytesCompat(256 * 1024)
                String(bytes).lines()
            }
            val fromFile = lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
            if (fromFile.isEmpty() || fromFile.any { !PACKAGE_NAME.matches(it) }) {
                return@withContext Result.InvalidFile
            }
            val current = detachedNow() ?: return@withContext Result.Failed(
                context.getString(R.string.error_no_module)
            )
            val merged = (current + fromFile).distinct()
            val added = merged.size - current.size
            if (added > 0) {
                val result = runShell("$DETACH_BIN detachall ${merged.joinToString(" ")}")
                if (!result.ok) return@withContext Result.Failed(result.err)
            }
            Result.Imported(added, merged.size)
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.toString())
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}

package com.example.llmcar.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

data class ModelEntry(
    val displayName: String,
    val path: String,
    val isSaf: Boolean,
    val size: Long
)

class ModelRepository(private val context: Context) {

    private fun builtinDirs() = listOf(
        File(context.getExternalFilesDir(null), "models"),
        File("/sdcard/Models"),
        File("/sdcard/Download"),
        File("/storage/emulated/0/Models")
    )

    fun scanBuiltin(): List<ModelEntry> = builtinDirs()
        .filter { it.exists() && it.isDirectory }
        .flatMap { dir ->
            dir.walkTopDown().maxDepth(2)
                .filter { it.isFile && it.extension.equals("gguf", true) }
                .take(50)
                .map { ModelEntry(it.name, it.absolutePath, false, it.length()) }
                .toList()
        }.distinctBy { it.path }

    fun scanTree(uriStr: String): List<ModelEntry> {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) ?: return emptyList()
        if (!root.isDirectory) return emptyList()
        return root.listFiles().asSequence()
            .filter { it.isFile && it.name?.endsWith(".gguf", true) == true }
            .take(200)
            .map { ModelEntry(it.name ?: "model.gguf", it.uri.toString(), true, it.length()) }
            .toList()
    }

    fun scanAll(treeUris: List<String>): List<ModelEntry> =
        (scanBuiltin() + treeUris.flatMap { scanTree(it) }).distinctBy { it.path }

    fun materializeSaf(e: ModelEntry, onProgress: (Long, Long) -> Unit = { _, _ -> }): File? {
        if (!e.isSaf) return File(e.path)
        val target = File(context.getExternalFilesDir(null), "models/${e.displayName}")
        if (target.exists() && target.length() == e.size) return target
        target.parentFile?.mkdirs()
        return try {
            val input = context.contentResolver.openInputStream(Uri.parse(e.path)) ?: return null
            input.use { i ->
                target.outputStream().use { o ->
                    val buf = ByteArray(1 shl 16); var c = 0L
                    while (true) {
                        val r = i.read(buf); if (r < 0) break
                        o.write(buf, 0, r); c += r; onProgress(c, e.size)
                    }
                }
            }
            target
        } catch (_: Exception) { target.delete(); null }
    }
}
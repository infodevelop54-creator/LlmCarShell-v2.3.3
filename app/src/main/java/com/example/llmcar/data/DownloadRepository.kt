package com.example.llmcar.data

import android.app.DownloadManager
import android.content.*
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.io.File

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Running(val percent: Int, val bytes: Long, val total: Long) : DownloadState()
    data class Done(val file: File) : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}

class DownloadRepository(private val context: Context) {

    private val dm: DownloadManager = context.getSystemService(DownloadManager::class.java)
    private var currentId: Long? = null

    fun modelsDir(): File =
        File(context.getExternalFilesDir(null), "models").apply { mkdirs() }

    fun download(url: String, fileName: String): Flow<DownloadState> = callbackFlow {
        trySend(DownloadState.Running(0, 0, 0))
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Загрузка модели LLM")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, "models/$fileName")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        val id = try { dm.enqueue(req) } catch (e: Exception) {
            trySend(DownloadState.Failed(e.message ?: "enqueue failed"))
            close(); return@callbackFlow
        }
        currentId = id

        val recv = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val r = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (r != id) return
                dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                    if (c.moveToFirst()) {
                        when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                            DownloadManager.STATUS_SUCCESSFUL ->
                                trySend(DownloadState.Done(File(modelsDir(), fileName)))
                            DownloadManager.STATUS_FAILED -> {
                                val reason = c.getInt(c.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_REASON))
                                trySend(DownloadState.Failed("reason=$reason"))
                            }
                        }
                    }
                }
                close()
            }
        }
        ContextCompat.registerReceiver(context, recv,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED)

        val job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                    if (c.moveToFirst()) {
                        val b = c.getLong(c.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val t = c.getLong(c.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        trySend(DownloadState.Running(
                            if (t > 0) ((b * 100) / t).toInt() else 0, b, t))
                    }
                }
                delay(1000)
            }
        }

        awaitClose {
            job.cancel()
            runCatching { context.unregisterReceiver(recv) }
        }
    }

    fun cancel() {
        currentId?.let { runCatching { dm.remove(it) } }
        currentId = null
    }
}
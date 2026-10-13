package com.example.llmcar.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.llmcar.data.CloudSyncRepository

class CloudSyncWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result = runCatching {
        CloudSyncRepository(applicationContext).performSyncBlocking()
        Result.success()
    }.getOrElse { Result.retry() }
}
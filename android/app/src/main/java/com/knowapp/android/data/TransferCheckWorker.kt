package com.knowapp.android.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.knowapp.android.KnowApplication

// Runs about every 15 minutes in the background to look for stock sent to this person
class TransferCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as KnowApplication).container
        val session = container.sessionStore.session.value ?: return Result.success()
        if (session.role == "super_admin" || session.role == "agent" || session.staffType == "teacher") return Result.success()
        TransferAlerts.check(applicationContext, container.exchangeRepository)
        runCatching { MemberDueAlerts.check(applicationContext, container) }
        return Result.success()
    }
}

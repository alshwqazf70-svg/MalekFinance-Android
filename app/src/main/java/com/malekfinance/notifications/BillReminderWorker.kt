package com.malekfinance.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.malekfinance.FinanceDatabaseProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.malekfinance.FinanceBillPolicy
import com.malekfinance.FinanceDb
import com.malekfinance.MainActivity
import java.util.concurrent.TimeUnit

object BillReminderScheduler {
    internal const val CHANNEL_ID = "malek_finance_bill_reminders"

    private fun workName(billId: Long) = "malek-finance-bill-reminder-$billId"

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureNotificationChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "تذكيرات الفواتير",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "تنبيهات مواعيد الفواتير في MK Finance — إدارة أموالك" }
            )
        }
    }

    fun schedule(context: Context, billId: Long, dueAt: Long) {
        val now = System.currentTimeMillis()
        val reminderAt = FinanceBillPolicy.reminderAt(dueAt, now)
        val request = OneTimeWorkRequestBuilder<BillReminderWorker>()
            .setInputData(workDataOf("billId" to billId, "scheduledDueAt" to dueAt))
            .setInitialDelay((reminderAt - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .addTag(workName(billId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(billId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, billId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(billId))
    }
}

class BillReminderWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val billId = inputData.getLong("billId", -1L)
        val scheduledDueAt = inputData.getLong("scheduledDueAt", -1L)
        if (billId <= 0L || scheduledDueAt <= 0L) return Result.failure()

        val db = try {
            FinanceDatabaseProvider.get(applicationContext)
        } catch (_: Exception) {
            return Result.retry()
        }

        try {
            val bill = db.billDao().findById(billId) ?: return Result.success()
            val occurrenceKey = FinanceBillPolicy.occurrenceKey(scheduledDueAt)
            if (!bill.isActive || bill.nextDueAt != scheduledDueAt ||
                bill.lastReminderOccurrenceKey == occurrenceKey
            ) return Result.success()
            val latestBill = db.billDao().findById(billId) ?: return Result.success()
            if (!latestBill.isActive || latestBill.nextDueAt != scheduledDueAt ||
                latestBill.lastReminderOccurrenceKey == occurrenceKey
            ) return Result.success()

            BillReminderScheduler.ensureNotificationChannel(applicationContext)
            if (!BillReminderScheduler.canPostNotifications(applicationContext)) return Result.success()

            val openApp = PendingIntent.getActivity(
                applicationContext,
                billId.hashCode(),
                Intent(applicationContext, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val isDue = System.currentTimeMillis() >= latestBill.nextDueAt
            val notification = NotificationCompat.Builder(applicationContext, BillReminderScheduler.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(if (isDue) "فاتورة مستحقة" else "تذكير بموعد فاتورة")
                .setContentText(latestBill.title + if (isDue) " — حان موعد الاستحقاق" else " — موعد الاستحقاق خلال 24 ساعة")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            NotificationManagerCompat.from(applicationContext).notify(billId.hashCode(), notification)
            db.billDao().markReminderDelivered(billId, scheduledDueAt, occurrenceKey)
            return Result.success()
        } catch (_: SecurityException) {
            return Result.success()
        } catch (_: Exception) {
            return Result.retry()
        }
    }
}

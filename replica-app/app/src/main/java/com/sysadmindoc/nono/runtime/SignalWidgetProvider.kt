package com.sysadmindoc.nono.runtime

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sysadmindoc.nono.MainActivity
import com.sysadmindoc.nono.R
import com.sysadmindoc.nono.data.SignalDatabase
import com.sysadmindoc.nono.model.NotificationContentState
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Glanceable metadata-only status. No title, body, package, notification key, or rule text is
 * ever placed in the RemoteViews tree.
 */
class SignalWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        updateScope.launch {
            try {
                val dao = SignalDatabase.get(context).notificationDao()
                val count = dao.readWidgetCount()
                val summaries = dao.readGroupSummaryCount()
                val latest = dao.readWidgetLatest()
                CaptureGate.load(context)
                val views = widgetViews(context, count, summaries, latest?.postedAtEpochMillis, latest?.contentState)
                withContext(Dispatchers.Main) { manager.updateAppWidget(appWidgetIds, views) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        // The listener broadcasts an update after every persisted notification. A fresh
        // CoroutineScope per broadcast leaves an orphaned Job behind each time, so the receiver
        // keeps one supervised scope for the life of the process.
        private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun requestUpdate(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, SignalWidgetProvider::class.java))
            if (ids.isEmpty()) return
            appContext.sendBroadcast(
                Intent(appContext, SignalWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }

        internal fun widgetViews(
            context: Context,
            count: Int,
            groupSummaryCount: Int,
            latestEpochMillis: Long?,
            latestContentState: String?,
        ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_signal_status).apply {
            setTextViewText(R.id.widget_count, countLabel(count, groupSummaryCount))
            setTextViewText(R.id.widget_latest, latestEpochMillis?.let { "Last metadata: ${formatTime(it)}" } ?: "Last metadata: —")
            setTextViewText(R.id.widget_provenance, if (CaptureGate.isPaused()) "Capture paused" else provenanceLabel(latestContentState))
            setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }

        /**
         * Says what the number counts.
         *
         * A group summary stands for its group, so it is not counted as an arrival. Leaving that
         * silent made the widget disagree with History for no visible reason.
         */
        internal fun countLabel(count: Int, groupSummaryCount: Int): String = when {
            count == 0 && groupSummaryCount == 0 -> "No metadata captured"
            groupSummaryCount == 0 -> "$count notifications"
            count == 0 -> "$groupSummaryCount group summaries, no notifications"
            else -> "$count notifications, plus $groupSummaryCount group summaries not counted"
        }

        private fun formatTime(epochMillis: Long): String =
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))

        private fun provenanceLabel(contentState: String?): String = when (contentState?.let { runCatching { NotificationContentState.valueOf(it) }.getOrNull() }) {
            NotificationContentState.HIDDEN_BY_SYSTEM -> "Latest: recorded as hidden by an earlier build"
            NotificationContentState.NOT_AVAILABLE -> "Latest: no content arrived"
            NotificationContentState.NOT_STORED -> "Latest: metadata only"
            NotificationContentState.AVAILABLE -> "Latest: content provenance available"
            null -> "Metadata capture active"
        }
    }
}

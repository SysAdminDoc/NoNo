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
import com.sysadmindoc.nono.data.SignalPreferences
import com.sysadmindoc.nono.model.NotificationContentState
import java.text.DateFormat
import java.util.Date
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the widget's number counts.
 *
 * One number answers one question, and the three worth asking are different: everything that
 * arrived, what a rule caught, and what the user kept. The label always names the scope, because
 * a bare number cannot say which of the three it is.
 *
 * @property noun what the number is counting, used in the widget label.
 */
enum class WidgetScope(val label: String, val singular: String, val plural: String) {
    ALL_CAPTURED("All captured", "notification", "notifications"),
    // Named as History names the same filter, so the two do not look like different things.
    RULE_MATCHED("Rule-triggered", "rule match", "rule matches"),
    STARRED("Starred", "starred notification", "starred notifications"),
    ;

    fun noun(count: Int): String = if (count == 1) singular else plural
}

/**
 * Which of the three counts a scope reports.
 *
 * A separate function rather than a `when` buried in the update, so a scope wired to the wrong
 * query fails a test instead of putting a plausible number under a label that means something else.
 */
fun countFor(scope: WidgetScope, allCaptured: Int, ruleMatched: Int, starred: Int): Int = when (scope) {
    WidgetScope.ALL_CAPTURED -> allCaptured
    WidgetScope.RULE_MATCHED -> ruleMatched
    WidgetScope.STARRED -> starred
}

/** Resolves a stored or displayed label, falling back to the default for anything unrecognized. */
fun widgetScope(label: String?): WidgetScope =
    WidgetScope.entries.firstOrNull { it.label.equals(label?.trim(), ignoreCase = true) }
        ?: WidgetScope.ALL_CAPTURED

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
                // An unreadable store must not take the process down from a home-screen refresh.
                val stored = SignalPreferences.get(context).data
                    .catch { emit(emptyPreferences()) }
                    .first()
                val scope = widgetScope(stored[SignalPreferences.settingKey(SignalPreferences.WIDGET_COUNT_SETTING)])
                val count = countFor(
                    scope,
                    allCaptured = dao.readWidgetCount(),
                    ruleMatched = dao.readRuleMatchedWidgetCount(),
                    starred = dao.readStarredWidgetCount(),
                )
                val summaries = dao.readGroupSummaryCount()
                val latest = dao.readWidgetLatest()
                CaptureGate.load(context)
                val views = widgetViews(context, scope, count, summaries, latest?.postedAtEpochMillis, latest?.contentState)
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
            scope: WidgetScope,
            count: Int,
            groupSummaryCount: Int,
            latestEpochMillis: Long?,
            latestContentState: String?,
        ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_signal_status).apply {
            setTextViewText(R.id.widget_count, countLabel(scope, count, groupSummaryCount))
            setTextViewText(R.id.widget_latest, latestEpochMillis?.let { "Last metadata: ${formatTime(it)}" } ?: "Last metadata: none yet")
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
         * silent made the widget disagree with History for no visible reason. The scope is named
         * for the same reason: three different questions have three different answers, and a bare
         * number cannot say which one it is answering.
         */
        internal fun countLabel(scope: WidgetScope, count: Int, groupSummaryCount: Int): String = when {
            scope != WidgetScope.ALL_CAPTURED && count == 0 -> "No ${scope.plural}"
            scope != WidgetScope.ALL_CAPTURED -> "$count ${scope.noun(count)}"
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

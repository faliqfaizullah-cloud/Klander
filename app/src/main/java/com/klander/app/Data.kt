package com.klander.app

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.TextStyle
import java.util.Locale

data class Ev(val id: Long, val title: String, val whenMs: Long)

object Store {
    fun load(c: Context): List<Ev> {
        val a = JSONArray(c.getSharedPreferences("k", 0).getString("e", "[]"))
        return (0 until a.length()).map { val o = a.getJSONObject(it); Ev(o.getLong("id"), o.getString("t"), o.getLong("w")) }.sortedBy { it.whenMs }
    }
    fun save(c: Context, l: List<Ev>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.id).put("t", it.title).put("w", it.whenMs)) }
        c.getSharedPreferences("k", 0).edit().putString("e", a.toString()).apply()
    }
}

object Sched {
    private fun pi(c: Context, e: Ev) = PendingIntent.getBroadcast(c, e.id.toInt(),
        Intent(c, AlarmReceiver::class.java).putExtra("id", e.id).putExtra("t", e.title),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun set(c: Context, e: Ev) {
        val t = maxOf(e.whenMs - 15 * 60000, System.currentTimeMillis() + 2000)
        if (e.whenMs < System.currentTimeMillis()) return
        c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c, e))
    }
    fun cancel(c: Context, e: Ev) = c.getSystemService(AlarmManager::class.java).cancel(pi(c, e))
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("k", "Klander", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(i.getLongExtra("id", 0).toInt(), Notification.Builder(c, "k")
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar).setContentTitle(i.getStringExtra("t"))
            .setContentText("Starting soon").setContentIntent(open).setAutoCancel(true).build())
        KWidget.refresh(c)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Store.load(c).forEach { Sched.set(c, it) }; KWidget.refresh(c) }
}

class KWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { ids.forEach { m.updateAppWidget(it, build(c)) } }
    companion object {
        fun build(c: Context): RemoteViews {
            val z = ZoneId.systemDefault(); val d = LocalDate.now(); val now = System.currentTimeMillis()
            val evs = Store.load(c).filter { Instant.ofEpochMilli(it.whenMs).atZone(z).toLocalDate() == d }
            val next = evs.firstOrNull { it.whenMs > now }
            val v = RemoteViews(c.packageName, R.layout.widget_klander)
            v.setTextViewText(R.id.day, d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase())
            v.setTextViewText(R.id.num, d.dayOfMonth.toString())
            v.setTextViewText(R.id.sticker, listOf("🐥", "🌼", "🍓", "🐰", "⭐", "🍀", "🧸")[d.dayOfMonth % 7])
            v.setTextViewText(R.id.ev, next?.let { "${Instant.ofEpochMilli(it.whenMs).atZone(z).toLocalTime().toString().take(5)} ${it.title}" } ?: "No more events")
            v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            return v
        }
        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, KWidget::class.java)).forEach { m.updateAppWidget(it, build(c)) }
        }
    }
}

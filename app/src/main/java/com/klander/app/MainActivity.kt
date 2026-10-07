package com.klander.app

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

val Yellow = Color(0xFFFFEB00); val Ink = Color(0xFF111111)
val Grey = Color(0xFFC4C4C4); val Dim = Color(0xFF8A8A8A); val Cream = Color(0xFFF3F3EF)

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33)
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28)
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        setContent { App() }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
fun Modifier.bounce(onClick: () -> Unit): Modifier {
    val hf = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val p by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (p) 0.9f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "s")
    return this.scale(s).clickable(src, null) { hf.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
}

@Composable
fun Bubble(t: String, onClick: () -> Unit) {
    Box(Modifier.size(46.dp).clip(CircleShape).background(Cream).bounce(onClick), Alignment.Center) { Text(t, fontSize = 18.sp) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App() {
    val c = LocalContext.current; val hf = LocalHapticFeedback.current; val z = ZoneId.systemDefault()
    var day by remember { mutableStateOf(LocalDate.now()) }
    var evs by remember { mutableStateOf(Store.load(c)) }
    var stars by remember { mutableStateOf(c.getSharedPreferences("k", 0).getStringSet("s", emptySet())!!.toSet()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var add by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var list by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    fun dateOf(e: Ev) = Instant.ofEpochMilli(e.whenMs).atZone(z).toLocalDate()
    fun hhmm(e: Ev) = Instant.ofEpochMilli(e.whenMs).atZone(z).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    val today = evs.filter { dateOf(it) == day }
    val upcoming = today.filter { it.whenMs > now }
    val next = upcoming.firstOrNull(); val after = upcoming.getOrNull(1)
    val diff = if (next != null) (next.whenMs - now) / 1000 else 0L
    val (a, b, unit) = when {
        diff >= 86400 -> Triple(diff / 86400, diff % 86400 / 3600, "day")
        diff >= 3600 -> Triple(diff / 3600, diff % 3600 / 60, "hr")
        else -> Triple(diff / 60, diff % 60, "min")
    }
    val prog by animateFloatAsState(if (next == null) 0f else (1f - min(diff, 900L) / 900f), tween(900), label = "p")

    fun toast(s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()
    fun buzz() = hf.performHapticFeedback(HapticFeedbackType.LongPress)
    fun imp(e: Ev) = Ev(e.id + 7, "Important: " + e.title, e.whenMs - 45 * 60000)
    fun commit(l: List<Ev>) { evs = l.sortedBy { it.whenMs }; Store.save(c, evs); KWidget.refresh(c) }
    fun remove(e: Ev) { Sched.cancel(c, e); Sched.cancel(c, imp(e)); commit(evs - e); toast("Deleted") }
    fun saveStars(s: Set<String>) { stars = s; c.getSharedPreferences("k", 0).edit().putStringSet("s", s).apply() }
    fun go(d: Long) { day = day.plusDays(d); hf.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    fun pick() { buzz(); DatePickerDialog(c, { _, y, m, d -> day = LocalDate.of(y, m + 1, d) }, day.year, day.monthValue - 1, day.dayOfMonth).show() }
    fun toggleStar() {
        val e = next ?: return toast("No upcoming event to mark")
        val k = e.id.toString()
        if (k in stars) { saveStars(stars - k); Sched.cancel(c, imp(e)); toast("Removed from important") }
        else { saveStars(stars + k); Sched.set(c, imp(e)); toast("Marked important: extra reminder 1 hr before") }
    }
    fun share() {
        val txt = if (today.isEmpty()) "No events on $day" else "Klander " + day.format(DateTimeFormatter.ofPattern("MMM d")) + "\n" + today.joinToString("\n") { hhmm(it) + "  " + it.title }
        c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt), "Share agenda"))
    }
    fun jumpNext() { val e = evs.firstOrNull { dateOf(it) > day }; if (e != null) day = dateOf(e) else toast("No later events") }
    fun reschedule(e: Ev) {
        val t = Instant.ofEpochMilli(e.whenMs).atZone(z).toLocalTime()
        TimePickerDialog(c, { _, h, m ->
            val n = e.copy(whenMs = dateOf(e).atTime(h, m).atZone(z).toInstant().toEpochMilli())
            Sched.cancel(c, e); Sched.cancel(c, imp(e))
            commit(evs - e + n); Sched.set(c, n)
            if (e.id.toString() in stars) Sched.set(c, imp(n))
            toast("Time updated")
        }, t.hour, t.minute, true).show()
    }

    Column(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding().padding(20.dp)) {
        Row(Modifier.fillMaxWidth()) {
            AnimatedContent(day, modifier = Modifier.weight(1f).clickable { pick() },
                transitionSpec = { (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut()) }, label = "d") { d ->
                Column {
                    Text(d.format(DateTimeFormatter.ofPattern("MMM d")), fontSize = 38.sp, fontWeight = FontWeight.Black, color = Ink)
                    Text("${today.size} Meetings", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Grey)
                }
            }
            Text("≡", fontSize = 30.sp, color = Ink, modifier = Modifier.bounce { list = 2 }.padding(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Bubble(if (next != null && next.id.toString() in stars) "★" else "☆") { toggleStar() }
            Row(Modifier.clip(RoundedCornerShape(50)).background(Cream).bounce { list = 1 }.padding(8.dp), horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                val cols = listOf(Color(0xFF222222), Color(0xFFD9A441), Color(0xFF9DB0CC), Color(0xFFC77D5B), Color(0xFF6B8F71))
                (today.ifEmpty { listOf(Ev(0, "+", 0)) }).take(5).forEachIndexed { i, e ->
                    Box(Modifier.size(36.dp).clip(CircleShape).background(cols[i % 5]), Alignment.Center) { Text(e.title.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            Bubble("✉") { share() }
        }
        Box(Modifier.fillMaxWidth().height(230.dp).pointerInput(day) {
            var dx = 0f
            detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = { if (dx > 120) go(-1) else if (dx < -120) go(1) }) { _, d -> dx += d }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2; val cy = size.height * 1.25f; val r = size.width * 0.62f
                for (i in 0..60) {
                    val ang = Math.toRadians(215.0 + i * 110.0 / 60)
                    val len = if (i % 5 == 0) 22f else 12f
                    drawLine(Grey, Offset(cx + r * cos(ang).toFloat(), cy + r * sin(ang).toFloat()),
                        Offset(cx + (r - len) * cos(ang).toFloat(), cy + (r - len) * sin(ang).toFloat()), 3f)
                }
                val m = Math.toRadians(215.0 + (0.2 + prog * 0.6) * 110.0)
                drawLine(Color(0xFFE5352B), Offset(cx + (r + 4) * cos(m).toFloat(), cy + (r + 4) * sin(m).toFloat()),
                    Offset(cx + (r - 40) * cos(m).toFloat(), cy + (r - 40) * sin(m).toFloat()), 5f)
            }
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", fontSize = 34.sp, color = Dim, modifier = Modifier.bounce { go(-1) }.padding(12.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(unit, color = Dim, fontSize = 13.sp)
                    Row {
                        Text("%02d".format(a), fontSize = 54.sp, fontWeight = FontWeight.Medium, color = Grey)
                        Text(":", fontSize = 54.sp, color = Grey)
                        Text("%02d".format(b), fontSize = 54.sp, fontWeight = FontWeight.Medium, color = if (next == null) Grey else Ink)
                    }
                    Text(if (next == null) "Nothing left" else "Before the start", color = Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("›", fontSize = 34.sp, color = Dim, modifier = Modifier.bounce { go(1) }.padding(12.dp))
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Yellow)
                .combinedClickable(onClick = { buzz(); next?.let { reschedule(it) } ?: toast("Tap + to add an event") },
                    onLongClick = { buzz(); next?.let { remove(it) } }).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("You Have\na Meeting", fontWeight = FontWeight.Bold, color = Ink, fontSize = 15.sp)
                Column {
                    Row { Text(next?.let { hhmm(it).substringBefore(":") } ?: "--", fontSize = 40.sp, color = Ink)
                        Text(":" + (next?.let { hhmm(it).substringAfter(":") } ?: "--"), fontSize = 40.sp, color = Ink.copy(.45f)) }
                    Text((if (next != null && next.id.toString() in stars) "★ " else "") + (next?.title ?: "Tap + to add"), fontWeight = FontWeight.Bold, color = Ink, fontSize = 15.sp)
                    Text("Tap: edit time · Hold: delete", color = Ink.copy(.55f), fontSize = 10.sp)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Cream)
                .clickable { buzz(); after?.let { reschedule(it) } ?: toast("No second event today") }.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(after?.title ?: "All clear", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                Column { Text(if (after != null) "at " + hhmm(after) else "to you", color = Dim, fontSize = 12.sp)
                    Text(if (after != null) "Up next after this one" else "No more events on this day", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 14.sp) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Cream).bounce { day = LocalDate.now(); toast("Today") }, Alignment.Center) { Text("💬") }
            Row(Modifier.clip(RoundedCornerShape(50)).background(Ink).padding(start = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Calls", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.bounce { list = 1 })
                Spacer(Modifier.width(20.dp))
                Box(Modifier.size(54.dp).clip(CircleShape).background(Ink).border(1.dp, Color.White.copy(.25f), CircleShape).bounce { title = ""; add = true }, Alignment.Center) { Text("+", color = Color.White, fontSize = 26.sp) }
            }
            Box(Modifier.size(54.dp).clip(CircleShape).background(Cream).bounce { jumpNext() }, Alignment.Center) { Text("▮▮▮", fontSize = 9.sp) }
        }
    }

    if (list != 0) {
        val rows = if (list == 1) today else evs.filter { it.whenMs >= now - 3600000 }
        AlertDialog(onDismissRequest = { list = 0 },
            title = { Text(if (list == 1) "Events on " + day.format(DateTimeFormatter.ofPattern("MMM d")) else "All upcoming") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                if (rows.isEmpty()) Text("Nothing here yet. Tap + to add an event.")
                rows.forEach { e ->
                    Row(Modifier.fillMaxWidth().clickable { day = dateOf(e); list = 0 }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text((if (e.id.toString() in stars) "★ " else "") + e.title, fontWeight = FontWeight.Bold)
                            Text(dateOf(e).toString() + "  " + hhmm(e), color = Dim, fontSize = 12.sp)
                        }
                        Text("✕", modifier = Modifier.bounce { remove(e) }.padding(10.dp))
                    }
                }
            } },
            confirmButton = { TextButton(onClick = { list = 0 }) { Text("Close") } })
    }

    if (add) AlertDialog(onDismissRequest = { add = false },
        title = { Text("New event") },
        text = { OutlinedTextField(title, { title = it }, placeholder = { Text("Development call") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            if (title.isBlank()) { toast("Type a title first") } else {
                add = false
                TimePickerDialog(c, { _, h, m ->
                    val e = Ev(System.currentTimeMillis(), title.trim(), day.atTime(h, m).atZone(z).toInstant().toEpochMilli())
                    commit(evs + e); Sched.set(c, e); buzz(); toast("Saved. Reminder set")
                }, 9, 0, true).show()
            }
        }) { Text("Pick time") } },
        dismissButton = { TextButton(onClick = { add = false }) { Text("Cancel") } })
}

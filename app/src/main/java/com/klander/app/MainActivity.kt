package com.klander.app

import android.Manifest
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
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

val Bg = Color(0xFF0A0A0A); val CellBg = Color(0xFF121212); val Line = Color(0xFF1F1F1F); val Dim = Color(0xFF8A8A8A)
val Pal = listOf(Color(0xFFB8C7D9), Color(0xFFF2EEE8), Color(0xFFE9E2F5), Color(0xFFFFD9C7), Color(0xFFD6EBD2), Color(0xFFFFE98A))
val Stk = listOf("🐥", "🌼", "🍓", "🐰", "⭐", "🍀", "🧸", "🦊", "🐙", "🍩", "🌙", "🐢", "🍒", "🦋", "🐻", "🍄", "🌈", "🐳", "🍋", "🐼", "🧁", "🐸", "🍉", "🐱")

fun stickerOf(t: String): String {
    val f = t.substringBefore(' ')
    return if (t.contains(' ') && f.isNotEmpty() && f.codePointAt(0) > 0x2000) f else Stk[abs(t.hashCode()) % Stk.size]
}

class StampShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { 2.5.dp.toPx() }; val step = r * 3.4f
        val rect = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
        val holes = Path().apply {
            var x = step / 2; while (x < size.width) { addOval(Rect(Offset(x, 0f), r)); addOval(Rect(Offset(x, size.height), r)); x += step }
            var y = step / 2; while (y < size.height) { addOval(Rect(Offset(0f, y), r)); addOval(Rect(Offset(size.width, y), r)); y += step }
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, rect, holes))
    }
}

@Composable
fun Stamp(e: String, bg: Color, mod: Modifier, fs: Int) {
    Box(mod.clip(StampShape()).background(bg), Alignment.Center) { Text(e, fontSize = fs.sp) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33)
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28)
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        setContent { App { finish() } }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
fun Modifier.press(onClick: () -> Unit): Modifier {
    val hf = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val p by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (p) 0.9f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "s")
    return this.scale(s).clickable(src, null) { hf.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun App(onClose: () -> Unit) {
    val c = LocalContext.current; val hf = LocalHapticFeedback.current; val z = ZoneId.systemDefault(); val scope = rememberCoroutineScope()
    val base = remember { LocalDate.now().withDayOfMonth(1) }
    val pager = rememberPagerState(300) { 600 }
    val month = base.plusMonths((pager.currentPage - 300).toLong())
    var evs by remember { mutableStateOf(Store.load(c)) }
    var stars by remember { mutableStateOf(c.getSharedPreferences("k", 0).getStringSet("s", emptySet())!!.toSet()) }
    var sel by remember { mutableStateOf(LocalDate.now()) }
    var mode by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf(Stk[0]) }

    fun dateOf(e: Ev) = Instant.ofEpochMilli(e.whenMs).atZone(z).toLocalDate()
    fun hhmm(e: Ev) = Instant.ofEpochMilli(e.whenMs).atZone(z).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    fun toast(s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()
    fun buzz() = hf.performHapticFeedback(HapticFeedbackType.LongPress)
    fun imp(e: Ev) = Ev(e.id + 7, "Important: " + e.title, e.whenMs - 45 * 60000)
    fun commit(l: List<Ev>) { evs = l.sortedBy { it.whenMs }; Store.save(c, evs); KWidget.refresh(c) }
    fun remove(e: Ev) { Sched.cancel(c, e); Sched.cancel(c, imp(e)); commit(evs - e); toast("Deleted") }
    fun saveStars(s: Set<String>) { stars = s; c.getSharedPreferences("k", 0).edit().putStringSet("s", s).apply() }
    fun star(e: Ev) {
        val k = e.id.toString()
        if (k in stars) { saveStars(stars - k); Sched.cancel(c, imp(e)); toast("Removed from important") }
        else { saveStars(stars + k); Sched.set(c, imp(e)); toast("Important: extra reminder 1 hr before") }
    }
    fun reschedule(e: Ev) {
        val t = Instant.ofEpochMilli(e.whenMs).atZone(z).toLocalTime()
        TimePickerDialog(c, { _, h, m ->
            val n = e.copy(whenMs = dateOf(e).atTime(h, m).atZone(z).toInstant().toEpochMilli())
            Sched.cancel(c, e); Sched.cancel(c, imp(e)); commit(evs - e + n); Sched.set(c, n)
            if (e.id.toString() in stars) Sched.set(c, imp(n)); toast("Time updated")
        }, t.hour, t.minute, true).show()
    }
    fun share() {
        val l = evs.filter { YearMonth.from(dateOf(it)) == YearMonth.from(month) }
        val txt = if (l.isEmpty()) "No events in ${month.month}" else "Klander " + month.format(DateTimeFormatter.ofPattern("MMMM yyyy")) + "\n" + l.joinToString("\n") { dateOf(it).dayOfMonth.toString() + "  " + hhmm(it) + "  " + it.title }
        c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt), "Share"))
    }
    LaunchedEffect(pager.currentPage) { hf.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val byDay = evs.groupBy { dateOf(it) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(Modifier.fillMaxSize()) {
                val g = 40.dp.toPx(); var x = 0f; while (x < size.width) { drawLine(Line, Offset(x, 0f), Offset(x, size.height), 1f); x += g }
                var y = 0f; while (y < size.height) { drawLine(Line, Offset(0f, y), Offset(size.width, y), 1f); y += g }
            }
            VerticalPager(pager, Modifier.fillMaxSize()) { page ->
                val pm = base.plusMonths((page - 300).toLong())
                val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                val own = evs.filter { YearMonth.from(dateOf(it)) == YearMonth.from(pm) }.map { stickerOf(it.title) }.distinct()
                val set = (own + (0 until 6).map { Stk[(pm.monthValue * 5 + it * 3) % Stk.size] }).take(6)
                BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - abs(off).coerceIn(0f, 1f) }) {
                    val w = maxWidth; val h = maxHeight
                    set.forEachIndexed { i, e ->
                        val sw = w * (0.27f + 0.02f * (i % 3))
                        Stamp(e, Pal[(pm.monthValue + i) % 6], Modifier.offset(w * (0.02f + 0.15f * i), h * (0.66f - 0.105f * i)).size(sw, sw * 1.2f)
                            .graphicsLayer { translationY = off * h.toPx() * (0.4f + 0.25f * i); rotationZ = ((i % 3) - 1) * 4f
                                val s = 1f - 0.15f * abs(off).coerceIn(0f, 1f); scaleX = s; scaleY = s }, 40)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(90.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Bg))))
            Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF2A2A2A)).press { onClose() }, Alignment.Center) { Text("✕", color = Color.White, fontSize = 16.sp) }
                Box {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF2A2A2A)).press { menu = true }, Alignment.Center) { Text("•••", color = Color.White, fontSize = 14.sp) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Today") }, { menu = false; scope.launch { pager.animateScrollToPage(300) }; sel = LocalDate.now() })
                        DropdownMenuItem({ Text("All upcoming") }, { menu = false; mode = 2 })
                        DropdownMenuItem({ Text("Share month") }, { menu = false; share() })
                    }
                }
            }
            AnimatedContent(month, Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 12.dp),
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }, label = "m") { m ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(m.year.toString(), color = Dim, fontSize = 18.sp)
                    Text(m.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(Modifier.fillMaxWidth().weight(1.15f).navigationBarsPadding().pointerInput(Unit) {
            var dx = 0f
            detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                val t = pager.currentPage + (if (dx < -100) 1 else if (dx > 100) -1 else 0)
                if (t != pager.currentPage) scope.launch { pager.animateScrollToPage(t) }
            }) { _, d -> dx += d }
        }) {
            Row(Modifier.fillMaxWidth().height(28.dp)) { listOf("S", "M", "T", "W", "T", "F", "S").forEach { Text(it, Modifier.weight(1f), color = Dim, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
            val first = month.withDayOfMonth(1); val off0 = first.dayOfWeek.value % 7; val len = month.lengthOfMonth()
            for (r in 0 until 6) Row(Modifier.fillMaxWidth().weight(1f)) {
                for (col in 0 until 7) {
                    val d = r * 7 + col - off0 + 1
                    Box(Modifier.weight(1f).fillMaxHeight().background(CellBg).border(0.5.dp, Line)
                        .clickable(enabled = d in 1..len) { buzz(); sel = month.withDayOfMonth(d); mode = 1 }, Alignment.Center) {
                        if (d in 1..len) {
                            val date = month.withDayOfMonth(d); val list = byDay[date]
                            if (list != null) Stamp(stickerOf(list[0].title), Pal[(d + list[0].title.length) % 6], Modifier.size(30.dp, 36.dp), 16)
                            else if (date == LocalDate.now()) Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White), Alignment.Center) { Text("$d", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            else Text("$d", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (mode != 0) {
        val rows = if (mode == 1) byDay[sel] ?: emptyList() else evs.filter { it.whenMs >= System.currentTimeMillis() - 3600000 }
        ModalBottomSheet({ mode = 0 }, containerColor = Color(0xFF161616)) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(if (mode == 1) sel.format(DateTimeFormatter.ofPattern("EEE, MMM d")) else "All upcoming", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (rows.isEmpty()) Text("No events yet. Tap Add event.", color = Dim, modifier = Modifier.padding(vertical = 12.dp))
                    rows.forEach { e ->
                        Row(Modifier.fillMaxWidth().clickable { buzz(); reschedule(e) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Stamp(stickerOf(e.title), Pal[e.title.length % 6], Modifier.size(34.dp, 40.dp), 18)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(e.title, color = Color.White, fontWeight = FontWeight.Bold)
                                Text((if (mode == 2) dateOf(e).toString() + "  " else "") + hhmm(e) + "  (tap to change time)", color = Dim, fontSize = 12.sp)
                            }
                            Text(if (e.id.toString() in stars) "★" else "☆", color = Color(0xFFFFE98A), fontSize = 22.sp, modifier = Modifier.press { star(e) }.padding(8.dp))
                            Text("✕", color = Color.White, modifier = Modifier.press { remove(e) }.padding(8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White).press { title = ""; add = true }.padding(14.dp), Alignment.Center) {
                    Text("+  Add event", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (add) AlertDialog({ add = false }, title = { Text("New event") },
        text = { Column {
            OutlinedTextField(title, { title = it }, placeholder = { Text("Development call") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Stk.forEach { s -> Text(s, fontSize = 26.sp, modifier = Modifier.padding(4.dp).clip(CircleShape).background(if (s == pick) Color(0xFFFFE98A) else Color.Transparent).clickable { pick = s; buzz() }.padding(6.dp)) }
            }
        } },
        confirmButton = { TextButton({
            if (title.isBlank()) toast("Type a title first") else {
                add = false
                TimePickerDialog(c, { _, h, m ->
                    val e = Ev(System.currentTimeMillis(), "$pick ${title.trim()}", sel.atTime(h, m).atZone(z).toInstant().toEpochMilli())
                    commit(evs + e); Sched.set(c, e); buzz(); toast("Saved. Reminder set")
                    if (YearMonth.from(sel) != YearMonth.from(month)) scope.launch { pager.animateScrollToPage(300 + java.time.temporal.ChronoUnit.MONTHS.between(base, sel.withDayOfMonth(1)).toInt()) }
                }, 9, 0, true).show()
            }
        }) { Text("Pick time") } },
        dismissButton = { TextButton({ add = false }) { Text("Cancel") } })
}

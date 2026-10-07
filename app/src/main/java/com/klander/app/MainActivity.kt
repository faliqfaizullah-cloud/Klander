package com.klander.app

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

val Beige = Color(0xFFE3E1D0); val Yellow = Color(0xFFFFEB00); val Ink = Color(0xFF111111)
val Grey = Color(0xFFB3B1A3); val Cream = Color(0xFFEDEBDE)

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33)
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { App() }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App() {
    val c = LocalContext.current; val hf = LocalHapticFeedback.current; val z = ZoneId.systemDefault()
    var day by remember { mutableStateOf(LocalDate.now()) }
    var evs by remember { mutableStateOf(Store.load(c)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var add by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val today = evs.filter { Instant.ofEpochMilli(it.whenMs).atZone(z).toLocalDate() == day }
    val upcoming = today.filter { it.whenMs > now }
    val next = upcoming.firstOrNull(); val after = upcoming.getOrNull(1)
    val diff = if (next != null) (next.whenMs - now) / 1000 else 0L
    val (a, b, unit) = if (diff >= 3600) Triple(diff / 3600, diff % 3600 / 60, "hr") else Triple(diff / 60, diff % 60, "min")
    val prog by animateFloatAsState(if (next == null) 0f else (1f - min(diff, 900L) / 900f), tween(900), label = "p")
    fun hhmm(e: Ev) = Instant.ofEpochMilli(e.whenMs).atZone(z).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    fun go(d: Long) { day = day.plusDays(d); hf.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    Column(Modifier.fillMaxSize().background(Beige).statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        AnimatedContent(day, transitionSpec = { (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut()) }, label = "d") { d ->
            Column {
                Text(d.format(DateTimeFormatter.ofPattern("MMM d")), fontSize = 38.sp, fontWeight = FontWeight.Black, color = Ink)
                Text("${today.size} Meetings", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Grey)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50)).background(Cream).padding(10.dp), horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            val cols = listOf(Color(0xFF222222), Color(0xFFD9A441), Color(0xFFB8C4D6), Color(0xFFC77D5B), Color(0xFF6B8F71))
            (today.ifEmpty { listOf(Ev(0, "+", 0)) }).take(5).forEachIndexed { i, e ->
                Box(Modifier.size(38.dp).clip(CircleShape).background(cols[i % 5]), Alignment.Center) { Text(e.title.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        Box(Modifier.fillMaxWidth().height(230.dp)) {
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
                Text("‹", fontSize = 34.sp, color = Grey, modifier = Modifier.bounce { go(-1) }.padding(12.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(unit, color = Grey, fontSize = 13.sp)
                    Row {
                        val col = if (next == null) Grey else Ink
                        Text("%02d".format(a), fontSize = 54.sp, fontWeight = FontWeight.Medium, color = Grey)
                        Text(":", fontSize = 54.sp, color = Grey)
                        Text("%02d".format(b), fontSize = 54.sp, fontWeight = FontWeight.Medium, color = col)
                    }
                    Text(if (next == null) "Nothing left today" else "Before the start", color = Grey, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("›", fontSize = 34.sp, color = Grey, modifier = Modifier.bounce { go(1) }.padding(12.dp))
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Yellow)
                .combinedClickable(onClick = {}, onLongClick = {
                    next?.let { e -> Sched.cancel(c, e); evs = evs - e; Store.save(c, evs); KWidget.refresh(c) }
                    hf.performHapticFeedback(HapticFeedbackType.LongPress) }).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("You Have\na Meeting", fontWeight = FontWeight.Bold, color = Ink, fontSize = 15.sp)
                Column {
                    Row { Text(next?.let { hhmm(it).substringBefore(":") } ?: "--", fontSize = 40.sp, color = Ink)
                        Text(":" + (next?.let { hhmm(it).substringAfter(":") } ?: "--"), fontSize = 40.sp, color = Ink.copy(.45f)) }
                    Text(next?.title ?: "Tap + to add", fontWeight = FontWeight.Bold, color = Ink, fontSize = 15.sp)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Cream).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(after?.title ?: "All clear", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                Column { Text(if (after != null) "at ${hhmm(after)}" else "to you", color = Grey, fontSize = 12.sp)
                    Text(if (after != null) "Your next event after this one" else "No more events on this day", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 14.sp) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White).bounce { day = LocalDate.now() }, Alignment.Center) { Text("💬") }
            Row(Modifier.clip(RoundedCornerShape(50)).background(Ink).padding(start = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Calls", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(20.dp))
                Box(Modifier.size(54.dp).clip(CircleShape).background(Ink).border(1.dp, Color.White.copy(.25f), CircleShape).bounce { title = ""; add = true }, Alignment.Center) { Text("+", color = Color.White, fontSize = 26.sp) }
            }
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White).bounce { go(1) }, Alignment.Center) { Text("▮▮▮", fontSize = 9.sp) }
        }
    }
    if (add) AlertDialog(onDismissRequest = { add = false },
        title = { Text("New event") },
        text = { OutlinedTextField(title, { title = it }, placeholder = { Text("Development call") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            if (title.isNotBlank()) {
                add = false
                TimePickerDialog(c, { _, h, m ->
                    val e = Ev(System.currentTimeMillis(), title.trim(), day.atTime(h, m).atZone(z).toInstant().toEpochMilli())
                    evs = (evs + e).sortedBy { it.whenMs }; Store.save(c, evs); Sched.set(c, e); KWidget.refresh(c)
                    hf.performHapticFeedback(HapticFeedbackType.LongPress)
                }, 9, 0, true).show()
            }
        }) { Text("Pick time") } },
        dismissButton = { TextButton(onClick = { add = false }) { Text("Cancel") } })
}

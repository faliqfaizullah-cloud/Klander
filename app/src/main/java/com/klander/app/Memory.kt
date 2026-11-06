package com.klander.app

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

data class Photo(val f: File, val t: Long)

fun loadPhotos(d: File): List<Photo> = (d.listFiles() ?: emptyArray())
    .filter { it.length() > 0 }
    .mapNotNull { f -> f.nameWithoutExtension.toLongOrNull()?.let { Photo(f, it) } }
    .sortedByDescending { it.t }

@Composable
fun Root(onClose: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(tab, label = "tab") { t -> if (t == 0) App(onClose) else MemoryScreen { tab = 0 } }
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp)
            .clip(RoundedCornerShape(50)).background(Color(0xE61C1C1E)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50)).padding(5.dp)) {
            listOf("📅" to "Calendar", "🖼" to "Memory").forEachIndexed { i, p ->
                Column(Modifier.clip(RoundedCornerShape(50)).background(if (tab == i) Color(0x33FFFFFF) else Color.Transparent)
                    .press { tab = i }.padding(horizontal = 22.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.first, fontSize = 16.sp)
                    Text(p.second, color = if (tab == i) Color.White else Dim, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun RoundBtn(t: String, onClick: () -> Unit) {
    Box(Modifier.padding(start = 8.dp).size(42.dp).clip(CircleShape).background(Color(0x33FFFFFF)).border(1.dp, Color(0x44FFFFFF), CircleShape).press(onClick), Alignment.Center) {
        Text(t, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun MonthCard(ym: YearMonth, list: List<Photo>, prog: () -> Float, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val name = ym.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    val h = ym.monthValue * 30f
    val fs = if (name.length <= 5) 104 else if (name.length <= 7) 84 else 66
    Box(Modifier.fillMaxWidth().height(210.dp)
        .graphicsLayer { val p = prog(); scaleX = 1f - 0.08f * p; scaleY = scaleX; alpha = 1f - 0.45f * p }
        .clip(RoundedCornerShape(28.dp)).press(onOpen)) {
        if (list.isEmpty()) Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.hsv(h, 0.45f, 0.5f), Color.hsv((h + 50f) % 360f, 0.6f, 0.22f)))))
        else AsyncImage(model = list[0].f, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x88000000)))))
        Text(name, softWrap = false, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart).offset(x = 10.dp, y = 26.dp),
            style = TextStyle(brush = Brush.verticalGradient(listOf(Color.White.copy(.9f), Color.White.copy(.25f))),
                fontSize = fs.sp, fontWeight = FontWeight.Bold, letterSpacing = (-3).sp))
        Box(Modifier.padding(14.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("${list.size} moments", color = Color.White, fontSize = 12.sp)
        }
        Box(Modifier.align(Alignment.TopEnd).padding(14.dp)) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0x33FFFFFF)).border(1.dp, Color(0x66FFFFFF), CircleShape).press { menu = true }, Alignment.Center) {
                Text("•••", color = Color.White, fontSize = 11.sp)
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Open") }, { menu = false; onOpen() })
                DropdownMenuItem({ Text("Share month") }, { menu = false; onShare() })
                DropdownMenuItem({ Text("Delete month") }, { menu = false; onDelete() })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemoryScreen(toCalendar: () -> Unit) {
    val c = LocalContext.current; val z = ZoneId.systemDefault(); val scope = rememberCoroutineScope()
    val dir = remember { File(c.filesDir, "memories").apply { mkdirs() } }
    var photos by remember { mutableStateOf(loadPhotos(dir)) }
    var year by remember { mutableStateOf(LocalDate.now().year) }
    var open by remember { mutableStateOf<YearMonth?>(null) }
    var last by remember { mutableStateOf(YearMonth.now()) }
    var viewer by remember { mutableStateOf<Int?>(null) }
    var yMenu by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<YearMonth?>(null) }
    var pending by remember { mutableStateOf<File?>(null) }
    fun toast(s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()
    val byMonth = photos.groupBy { YearMonth.from(Instant.ofEpochMilli(it.t).atZone(z)) }
    val years = (photos.map { Instant.ofEpochMilli(it.t).atZone(z).year } + LocalDate.now().year + (LocalDate.now().year + 1)).distinct().sortedDescending()

    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) { photos = loadPhotos(dir); toast("Moment saved") } else pending?.delete()
    }
    val gal = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) scope.launch(Dispatchers.IO) {
            val t = System.currentTimeMillis()
            uris.forEachIndexed { i, u ->
                runCatching { c.contentResolver.openInputStream(u)?.use { inp -> File(dir, "${t + i}.jpg").outputStream().use { out -> inp.copyTo(out) } } }
            }
            withContext(Dispatchers.Main) { photos = loadPhotos(dir); toast("Added ${uris.size} moments") }
        }
    }
    fun capture() {
        val f = File(dir, "${System.currentTimeMillis()}.jpg"); pending = f
        cam.launch(FileProvider.getUriForFile(c, c.packageName + ".fp", f))
    }
    fun share(l: List<Photo>) {
        if (l.isEmpty()) { toast("No moments to share"); return }
        val uris = ArrayList(l.map { FileProvider.getUriForFile(c, c.packageName + ".fp", it.f) })
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val cd = ClipData.newRawUri("", uris[0]); uris.drop(1).forEach { cd.addItem(ClipData.Item(it)) }
        i.clipData = cd
        c.startActivity(Intent.createChooser(i, "Share moments"))
    }
    fun removeFiles(l: List<Photo>) { l.forEach { it.f.delete() }; photos = loadPhotos(dir) }

    val state = rememberLazyListState()
    LaunchedEffect(year) { state.scrollToItem(if (year == LocalDate.now().year) (LocalDate.now().monthValue - 1).coerceAtLeast(0) else 0) }
    BackHandler(viewer != null || open != null) { if (viewer != null) viewer = null else open = null }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0F14))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Text("$year ⌄", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.press { yMenu = true })
                    DropdownMenu(yMenu, { yMenu = false }) { years.forEach { y -> DropdownMenuItem({ Text("$y") }, { year = y; yMenu = false }) } }
                }
                Spacer(Modifier.weight(1f))
                RoundBtn("📷") { capture() }
                RoundBtn("＋") { gal.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                RoundBtn("📅") { toCalendar() }
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), state = state, contentPadding = PaddingValues(top = 6.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(12) { idx ->
                    val ym = YearMonth.of(year, idx + 1)
                    val list = byMonth[ym] ?: emptyList()
                    MonthCard(ym, list,
                        prog = {
                            val li = state.layoutInfo; val it0 = li.visibleItemsInfo.firstOrNull { x -> x.index == idx }
                            if (it0 == null) 1f else min(1f, abs(it0.offset + it0.size / 2f - (li.viewportStartOffset + li.viewportEndOffset) / 2f) / max(1f, li.viewportEndOffset.toFloat()))
                        },
                        onOpen = { last = ym; open = ym }, onShare = { share(list) }, onDelete = { del = ym })
                }
            }
        }

        AnimatedVisibility(open != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            val list = byMonth[last] ?: emptyList()
            Column(Modifier.fillMaxSize().background(Color(0xFF0B0F14)).safeDrawingPadding().padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", color = Color.White, fontSize = 36.sp, modifier = Modifier.press { open = null }.padding(horizontal = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(last.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) + " " + last.year, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("${list.size} moments", color = Dim, fontSize = 13.sp)
                    }
                    RoundBtn("↗") { share(list) }
                    RoundBtn("📷") { capture() }
                }
                if (list.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No moments yet.\nTap 📷 to capture one.", color = Dim, textAlign = TextAlign.Center) }
                else LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(list.size) { i ->
                        AsyncImage(model = list[i].f, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { viewer = i })
                    }
                }
            }
        }

        val vs = viewer
        val vlist = byMonth[last] ?: emptyList()
        if (vs != null && vlist.isNotEmpty()) {
            val ps = rememberPagerState(vs.coerceIn(0, vlist.size - 1)) { vlist.size }
            val cur = vlist[ps.currentPage.coerceIn(0, vlist.size - 1)]
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                HorizontalPager(ps, Modifier.fillMaxSize()) { i ->
                    AsyncImage(model = vlist[i].f, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundBtn("✕") { viewer = null }
                    Text(Instant.ofEpochMilli(cur.t).atZone(z).format(DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm")), color = Color.White, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    RoundBtn("↗") { share(listOf(cur)) }
                    RoundBtn("🗑") { removeFiles(listOf(cur)); viewer = null; toast("Deleted") }
                }
            }
        }
    }

    del?.let { ym ->
        AlertDialog({ del = null }, title = { Text("Delete all moments?") }, text = { Text("This removes every photo in this month from Klander.") },
            confirmButton = { TextButton({ removeFiles(byMonth[ym] ?: emptyList()); del = null; toast("Deleted") }) { Text("Delete") } },
            dismissButton = { TextButton({ del = null }) { Text("Cancel") } })
    }
}

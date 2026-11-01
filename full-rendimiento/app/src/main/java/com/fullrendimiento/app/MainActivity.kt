package com.fullrendimiento.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

data class AppItem(val pkg: String, val label: String, val icon: ImageBitmap, val isGame: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GlassBackground { HomeScreen() } }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Pantalla principal
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var shizuku by remember { mutableStateOf(Shell.state()) }
    var opts by remember { mutableStateOf(Optimizer.loadOpts(ctx)) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var gamePkg by remember { mutableStateOf(Optimizer.selectedGame(ctx)) }
    var active by remember { mutableStateOf(Optimizer.isActive(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<Pair<String, List<Step>>?>(null) }
    var picker by remember { mutableStateOf(false) }
    var temp by remember { mutableStateOf(GameService.batteryTemp(ctx)) }
    var level by remember { mutableStateOf<Int?>(null) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Estado de Shizuku y de la sesion (se refresca solo)
    LaunchedEffect(Unit) {
        while (true) {
            shizuku = Shell.state()
            active = Optimizer.isActive(ctx)
            delay(1500)
        }
    }
    // Lista de apps instaladas
    LaunchedEffect(Unit) { apps = loadApps(ctx) }
    // Temperatura y bateria en vivo
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                if (t != Int.MIN_VALUE) temp = t / 10f
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0) level = l * 100 / s
            }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(r) }
    }

    val game = apps.firstOrNull { it.pkg == gamePkg }
    val ready = shizuku == Shell.State.READY
    fun setOpts(o: Opts) { opts = o; Optimizer.saveOpts(ctx, o) }

    fun boost() {
        val g = game ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        busy = true
        scope.launch {
            val steps = withContext(Dispatchers.IO) { Optimizer.apply(ctx, g.pkg, opts) }
            GameService.start(ctx, g.pkg, g.label)
            active = true
            busy = false
            results = "Optimizado para ${g.label}" to steps
            ctx.packageManager.getLaunchIntentForPackage(g.pkg)?.let {
                ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun restore() {
        busy = true
        scope.launch {
            val steps = withContext(Dispatchers.IO) { Optimizer.restore(ctx) }
            ctx.stopService(Intent(ctx, GameService::class.java))
            active = Optimizer.isActive(ctx)
            busy = false
            results = "Todo restaurado" to steps
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Header(temp, level)

        if (!ready) ShizukuCard(shizuku) { shizuku = Shell.state() }

        GameCard(game, gamePkg != null && game == null && apps.isNotEmpty(), enabled = !active) { picker = true }

        // ── Optimizaciones ──
        Glass(Modifier.fillMaxWidth()) {
            Title("Optimizaciones")
            Sub("Se aplican al pulsar el botón y se revierten solas al cerrar el juego.", size = 12)
            Spacer(Modifier.height(6.dp))
            val en = !active
            OptionRow("🚀", "Modo rendimiento", "Android prioriza el juego", opts.gameMode, en) { setOpts(opts.copy(gameMode = it)) }
            OptionRow("🧹", "Cerrar apps de fondo", "Libera RAM y procesador", opts.killBg, en) { setOpts(opts.copy(killBg = it)) }
            OptionRow("🖥️", "Pantalla a 120 Hz", "Máxima fluidez de tu A35", opts.refresh, en) { setOpts(opts.copy(refresh = it)) }
            OptionRow("🌀", "Sin animaciones", "Menos carga del sistema", opts.noAnim, en) { setOpts(opts.copy(noAnim = it)) }
            OptionRow("🔕", "No molestar", "Sin notificaciones mientras juegas", opts.dnd, en) { setOpts(opts.copy(dnd = it)) }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBubble("🎯")
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Resolución del juego", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Sub("Menos resolución = más FPS (algunos juegos no lo permiten)", size = 12)
                }
            }
            Spacer(Modifier.height(10.dp))
            Segmented(
                listOf(100 to "100%", 90 to "90%", 80 to "80%", 70 to "70%"), opts.scale, enabled = !active
            ) { setOpts(opts.copy(scale = it)) }
        }

        // ── Boton principal ──
        if (active) {
            BoostButton("⚡ ACTIVO", "Toca para restaurar todo ahora", enabled = !busy, active = true) { restore() }
            GlassButton("Abrir el juego", Modifier.fillMaxWidth(), enabled = game != null) {
                game?.let { g ->
                    ctx.packageManager.getLaunchIntentForPackage(g.pkg)?.let {
                        ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        } else {
            val sub = when {
                !ready -> "Primero conecta Shizuku"
                game == null -> "Primero elige tu juego"
                busy -> "Optimizando…"
                else -> "Optimiza y abre ${game.label}"
            }
            BoostButton("⚡ FULL RENDIMIENTO", sub, enabled = ready && game != null && !busy, active = false) { boost() }
        }

        // ── Resultado ──
        AnimatedVisibility(results != null) {
            results?.let { (title, steps) -> ResultsCard(title, steps) { results = null } }
        }

        TipsCard()
        Spacer(Modifier.height(10.dp))
    }

    if (picker) {
        GamePicker(apps, gamePkg, onDismiss = { picker = false }) { pkg ->
            gamePkg = pkg
            Optimizer.selectGame(ctx, pkg)
            picker = false
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Secciones
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun Header(temp: Float?, level: Int?) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(AccentBrush)
                .border(1.dp, Color.White.copy(0.4f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) { Text("⚡", fontSize = 26.sp) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text("Full Rendimiento", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Sub("Samsung Galaxy A35", size = 12, color = TextFaint)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val t = temp
            Chip(
                if (t != null) "${t.toInt()}°C" else "—°C",
                when { t == null -> TextFaint; t >= 42f -> Bad; t >= 38f -> Warn; else -> Ok }
            )
            if (level != null) Chip("$level%", if (level <= 20) Bad else Cyan)
        }
    }
}

@Composable
private fun ShizukuCard(state: Shell.State, onRefresh: () -> Unit) {
    val ctx = LocalContext.current
    val installed = remember {
        runCatching { ctx.packageManager.getPackageInfo(SHIZUKU_PKG, 0); true }.getOrDefault(false)
    }
    Glass(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble("🔑")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Title(if (state == Shell.State.NO_PERMISSION) "Permite el acceso" else "Conecta Shizuku")
                Sub(
                    if (state == Shell.State.NO_PERMISSION) "Shizuku está activo. Solo falta dar permiso."
                    else "Es lo que permite optimizar el sistema sin root.", size = 12
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            state == Shell.State.NO_PERMISSION -> GlassButton("Dar permiso", Modifier.fillMaxWidth()) {
                runCatching { Shizuku.requestPermission(1) }
                onRefresh()
            }
            !installed -> {
                Sub("1. Instala Shizuku desde la Play Store.\n2. Ábrelo y pulsa “Iniciar” (vía Depuración inalámbrica).\n3. Vuelve aquí y da permiso.", size = 13)
                Spacer(Modifier.height(12.dp))
                GlassButton("Instalar Shizuku", Modifier.fillMaxWidth()) {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PKG"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            else -> {
                Sub("Abre Shizuku y pulsa “Iniciar” en la sección de Depuración inalámbrica. Después de reiniciar el celular hay que volver a iniciarlo.", size = 13)
                Spacer(Modifier.height(12.dp))
                GlassButton("Abrir Shizuku", Modifier.fillMaxWidth()) {
                    ctx.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)?.let {
                        ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCard(game: AppItem?, missing: Boolean, enabled: Boolean, onPick: () -> Unit) {
    Glass(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (game != null) {
                Image(game.icon, null, Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)))
            } else {
                EmojiBubble("🎮", 58)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Sub("Juego", size = 12, color = TextFaint)
                Text(
                    game?.label ?: if (missing) "El juego ya no está instalado" else "Elige tu juego",
                    color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1
                )
            }
            GlassButton(if (game == null) "Elegir" else "Cambiar", enabled = enabled, onClick = onPick)
        }
    }
}

@Composable
private fun ResultsCard(title: String, steps: List<Step>, onClose: () -> Unit) {
    Glass(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title(title, Modifier.weight(1f))
            Text("✕", color = TextSub, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onClose).padding(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (steps.isEmpty()) Sub("No había nada que cambiar.")
        steps.forEach { s ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Text(if (s.ok) "✅" else "⚠️", fontSize = 14.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(s.name, color = TextMain, fontSize = 14.sp)
                    if (!s.ok && s.detail.isNotBlank()) Sub(s.detail, size = 12, color = TextFaint)
                }
            }
        }
    }
}

@Composable
private fun TipsCard() {
    var open by remember { mutableStateOf(false) }
    Glass(Modifier.fillMaxWidth().clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble("💡")
            Spacer(Modifier.width(12.dp))
            Title("Más FPS en tu A35", Modifier.weight(1f))
            Text(if (open) "▲" else "▼", color = TextSub, fontSize = 12.sp)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Sub("• Gaming Hub › ⋮ › Game Booster › Optimización del juego › Rendimiento.")
                Sub("• Apaga RAM Plus: Ajustes › Mantenimiento › Memoria › RAM Plus (usa el almacenamiento y es más lento que la RAM).")
                Sub("• El calor baja los FPS: juega sin funda y sin cargar.")
                Sub("• Los ms (ping) dependen de tu internet: usa Wi-Fi de 5 GHz cerca del router.")
                Sub("• Ninguna app puede añadir RAM real. Desconfía de las que lo prometen.", color = TextFaint)
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Selector de juego
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun GamePicker(apps: List<AppItem>, selected: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var onlyGames by remember { mutableStateOf(apps.any { it.isGame }) }
    val shown = apps.filter {
        (!onlyGames || it.isGame) && (query.isBlank() || it.label.contains(query, true))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xCC07081A)).clickable(onClick = onDismiss)) {
            Glass(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 620.dp)
                    .background(Color(0xE6141632), RoundedCornerShape(26.dp))
                    .clickable(enabled = false) {}
            ) {
                Title("Elige tu juego")
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clip(CircleShape)
                        .background(Color.White.copy(0.10f)).border(1.dp, Color.White.copy(0.25f), CircleShape)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) Sub("🔍  Buscar…", color = TextFaint, size = 14)
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = TextMain, fontSize = 15.sp),
                        cursorBrush = SolidColor(Cyan),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(10.dp))
                Segmented(listOf(true to "Solo juegos", false to "Todas las apps"), onlyGames) { onlyGames = it }
                Spacer(Modifier.height(8.dp))
                if (apps.isEmpty()) Sub("Cargando apps…", Modifier.padding(16.dp))
                else if (shown.isEmpty()) Sub(
                    if (onlyGames) "No encontré juegos marcados como tales. Prueba “Todas las apps”." else "Sin resultados.",
                    Modifier.padding(16.dp)
                )
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(shown, key = { it.pkg }) { app ->
                        val sel = app.pkg == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (sel) Violet.copy(0.28f) else Color.Transparent)
                                .clickable { onPick(app.pkg) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(app.icon, null, Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)))
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, color = TextMain, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            if (app.isGame) Text("🎮", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

suspend fun loadApps(ctx: Context): List<AppItem> = withContext(Dispatchers.IO) {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != ctx.packageName }
        .mapNotNull { ai ->
            runCatching {
                @Suppress("DEPRECATION")
                val isGame = ai.category == ApplicationInfo.CATEGORY_GAME ||
                    (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                AppItem(
                    ai.packageName,
                    ai.loadLabel(pm).toString(),
                    ai.loadIcon(pm).toBitmap(128, 128).asImageBitmap(),
                    isGame
                )
            }.getOrNull()
        }
        .sortedWith(compareByDescending<AppItem> { it.isGame }.thenBy { it.label.lowercase() })
}

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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
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
const val CREATOR = "Odiseo.co"

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
    var level by remember { mutableStateOf(Priv.level(ctx)) }
    var opts by remember { mutableStateOf(Optimizer.loadOpts(ctx)) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var gamePkg by remember { mutableStateOf(Optimizer.selectedGame(ctx)) }
    var active by remember { mutableStateOf(Optimizer.isActive(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<Pair<String, List<Step>>?>(null) }
    var picker by remember { mutableStateOf(false) }
    var temp by remember { mutableStateOf(GameService.batteryTemp(ctx)) }
    var battery by remember { mutableStateOf<Int?>(null) }
    var wifi by remember { mutableStateOf<Net.WifiState?>(null) }
    var granted by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }   // fuerza a recalcular disponibilidades

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Estado de Shizuku, nivel de acceso, sesion y Wi-Fi (se refresca solo)
    LaunchedEffect(Unit) {
        while (true) {
            val st = withContext(Dispatchers.IO) { Shell.state() }
            shizuku = st
            if (st == Shell.State.READY && !granted) {
                granted = true
                val changed = withContext(Dispatchers.IO) { Priv.selfGrant(ctx) }
                if (changed) results = "Permisos permanentes listos" to listOf(
                    Step("Desde ahora la mayoría de opciones funcionan sin Shizuku, incluso tras reiniciar", true)
                )
            }
            level = Priv.level(ctx)
            active = Optimizer.isActive(ctx)
            wifi = withContext(Dispatchers.IO) { Net.wifi(ctx) }
            tick++
            delay(2000)
        }
    }
    LaunchedEffect(Unit) { apps = loadApps(ctx) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                if (t != Int.MIN_VALUE) temp = t / 10f
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0) battery = l * 100 / s
            }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(r) }
    }

    val game = apps.firstOrNull { it.pkg == gamePkg }
    fun setOpts(o: Opts) { opts = o; Optimizer.saveOpts(ctx, o) }
    // Se recalculan en cada refresco (tick) por si se activa o se cae Shizuku
    val needSh = tick.let { Optimizer.missing(ctx, Need.SHIZUKU) }
    val needPerm = tick.let { Optimizer.missing(ctx, Need.PERMANENT) }
    val needDnd = tick.let { Optimizer.dndMissing(ctx) }

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
            if (!Optimizer.pendingRestore(ctx)) ctx.stopService(Intent(ctx, GameService::class.java))
            active = Optimizer.isActive(ctx)
            busy = false
            results = (if (active) "Falta restaurar algo" else "Todo restaurado") to steps
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
        Header(temp, battery, wifi)

        AccessCard(shizuku, level)

        GameCard(game, gamePkg != null && game == null && apps.isNotEmpty(), enabled = !active) { picker = true }

        // ── Rendimiento ──
        Glass(Modifier.fillMaxWidth()) {
            Title("⚡ Rendimiento")
            Sub("Se aplica al pulsar el botón y se revierte solo al cerrar el juego.", size = 12)
            Spacer(Modifier.height(6.dp))
            val en = !active
            OptionRow("🚀", "Modo rendimiento", "Android prioriza el juego", opts.gameMode, en, needSh) { setOpts(opts.copy(gameMode = it)) }
            OptionRow("🧹", "Cerrar apps de fondo", if (shizuku == Shell.State.READY) "Libera RAM y procesador" else "Modo básico (con Shizuku cierra más)", opts.killBg, en) { setOpts(opts.copy(killBg = it)) }
            OptionRow("🖥️", "Pantalla a 120 Hz", "Máxima fluidez de tu A35", opts.refresh, en, needSh) { setOpts(opts.copy(refresh = it)) }
            OptionRow("🌀", "Sin animaciones", "Menos carga del sistema", opts.noAnim, en, needPerm) { setOpts(opts.copy(noAnim = it)) }
            OptionRow("🔕", "No molestar", "Sin notificaciones mientras juegas", opts.dnd, en, needDnd) { setOpts(opts.copy(dnd = it)) }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBubble("🎯")
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Resolución del juego", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Sub("Menos resolución = más FPS (algunos juegos no lo permiten)", size = 12)
                    if (needSh != null) Text("🔒 $needSh", color = Warn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            Segmented(
                listOf(100 to "100%", 90 to "90%", 80 to "80%", 70 to "70%"), opts.scale,
                enabled = !active && needSh == null
            ) { setOpts(opts.copy(scale = it)) }
        }

        // ── Red gamer ──
        NetworkCard(opts, enabled = !active, needSh = needSh, needPerm = needPerm, tick = tick) { setOpts(it) }

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
                game == null -> "Primero elige tu juego"
                busy -> "Optimizando…"
                else -> "Optimiza y abre ${game.label}"
            }
            BoostButton("⚡ FULL RENDIMIENTO", sub, enabled = game != null && !busy, active = false) { boost() }
        }

        AnimatedVisibility(results != null) {
            results?.let { (title, steps) -> ResultsCard(title, steps) { results = null } }
        }

        NetTestCard()

        TipsCard()

        Footer()
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
private fun Header(temp: Float?, battery: Int?, wifi: Net.WifiState?) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(AccentBrush)
                .border(1.dp, Color.White.copy(0.4f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) { Text("⚡", fontSize = 26.sp) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text("Full Rendimiento", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Sub("Samsung Galaxy A35 · por $CREATOR", size = 12, color = TextFaint)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val t = temp
            Chip(
                if (t != null) "${t.toInt()}°C" else "—°C",
                when { t == null -> TextFaint; t >= 42f -> Bad; t >= 38f -> Warn; else -> Ok }
            )
            if (wifi != null) Chip(wifi.band, if (wifi.band == "2.4 GHz") Warn else Cyan)
            else if (battery != null) Chip("$battery%", if (battery <= 20) Bad else Cyan)
        }
    }
}

@Composable
private fun AccessCard(state: Shell.State, level: Priv.Level) {
    val ctx = LocalContext.current
    val installed = remember {
        runCatching { ctx.packageManager.getPackageInfo(SHIZUKU_PKG, 0); true }.getOrDefault(false)
    }
    Glass(Modifier.fillMaxWidth(), padding = 16) {
        val (emoji, title, sub) = when (level) {
            Priv.Level.FULL -> listOf("🟢", "Acceso completo", "Shizuku activo: todas las opciones disponibles.")
            Priv.Level.PERMANENT -> listOf("🟡", "Acceso permanente", "La mayoría funciona sin Shizuku. Actívalo para el 100%.")
            Priv.Level.BASIC -> listOf(
                "🔑", if (state == Shell.State.NO_PERMISSION) "Permite el acceso" else "Activa Shizuku una vez",
                if (state == Shell.State.NO_PERMISSION) "Shizuku está activo. Solo falta dar permiso."
                else "Con una sola vez, la app queda con acceso permanente."
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble(emoji)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Title(title)
                Sub(sub, size = 12)
            }
            if (level == Priv.Level.FULL) Chip("Shizuku", Ok)
            else if (level == Priv.Level.PERMANENT) Chip("Permanente", Warn)
        }
        if (level == Priv.Level.FULL) return@Glass
        Spacer(Modifier.height(12.dp))
        when {
            state == Shell.State.NO_PERMISSION -> GlassButton("Dar permiso", Modifier.fillMaxWidth()) {
                runCatching { Shizuku.requestPermission(1) }
            }
            !installed -> {
                if (level == Priv.Level.BASIC) {
                    Sub("1. Instala Shizuku desde la Play Store.\n2. Ábrelo y pulsa “Iniciar” (vía Depuración inalámbrica).\n3. Vuelve aquí y da permiso.", size = 13)
                    Spacer(Modifier.height(12.dp))
                }
                GlassButton("Instalar Shizuku", Modifier.fillMaxWidth()) {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PKG"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            else -> {
                if (level == Priv.Level.BASIC) {
                    Sub("Abre Shizuku y pulsa “Iniciar” en Depuración inalámbrica. Solo hace falta una vez: después la app conserva el acceso aunque reinicies.", size = 13)
                    Spacer(Modifier.height(12.dp))
                }
                GlassButton(if (level == Priv.Level.BASIC) "Abrir Shizuku" else "Activar Shizuku (opcional)", Modifier.fillMaxWidth()) {
                    ctx.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)?.let {
                        ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkCard(
    opts: Opts, enabled: Boolean, needSh: String?, needPerm: String?, tick: Int, onChange: (Opts) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dns by remember { mutableStateOf<Net.Dns?>(null) }
    var dnsBusy by remember { mutableStateOf(false) }
    var dnsMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tick / 5) { if (!dnsBusy) dns = withContext(Dispatchers.IO) { Net.currentDns(ctx) } }
    val canDns = Net.canSetDns(ctx)

    Glass(Modifier.fillMaxWidth()) {
        Title("📶 Red gamer")
        Sub("Menos picos de lag mientras juegas y descargas que sí funcionen.", size = 12)
        Spacer(Modifier.height(6.dp))
        OptionRow("📡", "Wi-Fi en baja latencia", "Apaga el ahorro de energía del Wi-Fi", opts.lowLatency, enabled, needSh) {
            onChange(opts.copy(lowLatency = it))
        }
        OptionRow("🛰️", "Pausar ubicación y búsquedas", "Evita escaneos que causan picos de ping", opts.quietRadios, enabled, needPerm) {
            onChange(opts.copy(quietRadios = it))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble("🌐")
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("DNS rápido", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Sub("Arregla descargas de mapas y armas por Wi-Fi. Queda fijo hasta que lo cambies.", size = 12)
                if (!canDns) Text("🔒 Activa Shizuku una vez", color = Warn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Segmented(
            Net.Dns.entries.map { it to it.label }, dns ?: Net.Dns.AUTO, enabled = canDns && !dnsBusy
        ) { d ->
            dnsBusy = true
            dnsMsg = "Probando ${d.label}…"
            scope.launch {
                val ok = withContext(Dispatchers.IO) { Net.setDns(ctx, d) }
                dns = withContext(Dispatchers.IO) { Net.currentDns(ctx) }
                dnsMsg = when {
                    ok && d == Net.Dns.AUTO -> "DNS automático de tu red."
                    ok -> "✅ ${d.label} activo y con internet."
                    else -> "⚠️ Tu red no permite ${d.label}: volví a Automático."
                }
                dnsBusy = false
            }
        }
        dnsMsg?.let { Spacer(Modifier.height(8.dp)); Sub(it, size = 12) }
    }
}

@Composable
private fun NetTestCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<Net.Report?>(null) }
    var testing by remember { mutableStateOf(false) }
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")

    Glass(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble("📊")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Title("Medidor de red")
                Sub("Revisa si tu Wi-Fi está listo para jugar.", size = 12)
            }
            report?.let {
                Chip(it.verdict, when (it.color) { 0 -> Ok; 1 -> Cyan; 2 -> Warn; else -> Bad })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (testing) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).rotate(angle).clip(CircleShape)
                        .border(3.dp, Brush.sweepGradient(listOf(Color.Transparent, Cyan, Violet)), CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Sub("Midiendo ping, estabilidad y DNS… (unos 8 segundos)")
            }
        }
        report?.let { r ->
            val w = r.wifi
            InfoRow("Conexión", when {
                w != null -> "Wi-Fi ${w.band}" + if (w.standard != "—") " · ${w.standard}" else ""
                r.mobile -> "Datos móviles"
                else -> "Sin red"
            }, if (w?.band == "2.4 GHz") Warn else TextMain)
            if (w != null) {
                InfoRow("Señal", "${w.signal} (${w.rssi} dBm)", when { w.rssi >= -65 -> Ok; w.rssi >= -72 -> Warn; else -> Bad })
                if (w.linkMbps > 0) InfoRow("Velocidad de enlace", "${w.linkMbps} Mbps")
            }
            r.router?.let { InfoRow("Ping al router", it.label, pingColor(it.avg, 8.0, 20.0, it.loss)) }
            r.internet?.let {
                InfoRow("Ping a internet", it.label, pingColor(it.avg, 40.0, 80.0, it.loss))
                if (it.loss < 100) {
                    InfoRow("Estabilidad (saltos)", "±${it.jitter.toInt()} ms", pingColor(it.jitter, 8.0, 20.0, 0))
                    InfoRow("Pérdida de paquetes", "${it.loss}%", if (it.loss == 0) Ok else if (it.loss < 5) Warn else Bad)
                }
            }
            InfoRow("DNS", r.dnsMs?.let { "$it ms" } ?: "falló", r.dnsMs?.let { if (it < 80) Ok else if (it < 150) Warn else Bad } ?: Bad)
            Spacer(Modifier.height(8.dp))
            r.tips.forEach { Sub("• $it", Modifier.padding(vertical = 2.dp), size = 12) }
            Spacer(Modifier.height(12.dp))
        }
        GlassButton(if (report == null) "Probar mi red" else "Probar de nuevo", Modifier.fillMaxWidth(), enabled = !testing) {
            testing = true
            scope.launch {
                report = withContext(Dispatchers.IO) { Net.test(ctx) }
                testing = false
            }
        }
    }
}

private fun pingColor(v: Double, good: Double, ok: Double, loss: Int): Color = when {
    loss >= 100 -> Bad
    v <= good -> Ok
    v <= ok -> Warn
    else -> Bad
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
                    if (s.detail.isNotBlank()) Sub(s.detail, size = 12, color = TextFaint)
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
            Title("Más FPS y menos lag", Modifier.weight(1f))
            Text(if (open) "▲" else "▼", color = TextSub, fontSize = 12.sp)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Sub("• Gaming Hub › ⋮ › Game Booster › Optimización del juego › Rendimiento.")
                Sub("• Apaga RAM Plus: Ajustes › Mantenimiento › Memoria › RAM Plus.")
                Sub("• El calor baja los FPS: juega sin funda y sin cargar.")
                Sub("• En casa usa tu red de 5 GHz. Fuera de casa (2.4 GHz) acércate al router.")
                Sub("• Elige en el juego el servidor de tu región (Latinoamérica): menos lag general.")
                Sub("• El lag de jugadores con VPN es de su conexión: ninguna app del celular lo puede evitar.", color = TextFaint)
            }
        }
    }
}

@Composable
private fun Footer() {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Creado por $CREATOR", color = TextSub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Sub("Full Rendimiento v2.0", size = 11, color = TextFaint)
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

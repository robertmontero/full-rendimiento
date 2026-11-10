package com.fullrendimiento.app

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.util.Locale

/** Opciones elegidas por el usuario. */
data class Opts(
    val gameMode: Boolean = true,
    val refresh: Boolean = true,
    val killBg: Boolean = true,
    val noAnim: Boolean = true,
    val dnd: Boolean = true,
    val scale: Int = 100,          // resolucion interna del juego: 100, 90, 80 o 70 %
    val lowLatency: Boolean = true,
    val quietRadios: Boolean = true,
)

/** Resultado de cada paso, para mostrarlo en pantalla. */
data class Step(val name: String, val ok: Boolean, val detail: String = "")

/** Que necesita cada optimizacion. */
enum class Need { NONE, PERMANENT, SHIZUKU }

/**
 * Aplica y revierte las optimizaciones. Guarda cada valor original antes de cambiarlo;
 * "Restaurar" solo borra la copia de lo que realmente pudo devolver.
 */
object Optimizer {

    private const val BACKUP = "backup"
    private const val SETTINGS = "settings"
    private val ANIM_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
    private val SCAN_KEYS = listOf("wifi_scan_always_enabled", "ble_scan_always_enabled")

    private fun backup(ctx: Context): SharedPreferences = ctx.getSharedPreferences(BACKUP, Context.MODE_PRIVATE)
    private fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

    fun isActive(ctx: Context) = backup(ctx).getBoolean("active", false)
    fun activePkg(ctx: Context): String? = backup(ctx).getString("pkg", null)

    // ── que se puede usar ahora ─────────────────────────────────────────────
    /** null = disponible; si no, el requisito que falta. */
    fun missing(ctx: Context, need: Need): String? = when (need) {
        Need.NONE -> null
        Need.PERMANENT -> if (Shell.ready || Priv.hasWss(ctx)) null else "Activa Shizuku una vez"
        Need.SHIZUKU -> if (Shell.ready) null else "Necesita Shizuku"
    }

    fun dndMissing(ctx: Context): String? =
        if (Shell.ready || Priv.hasDndAccess(ctx)) null else "Activa Shizuku una vez"

    // ── preferencias de la interfaz ─────────────────────────────────────────
    fun loadOpts(ctx: Context): Opts = prefs(ctx).run {
        Opts(
            gameMode = getBoolean("gameMode", true),
            refresh = getBoolean("refresh", true),
            killBg = getBoolean("killBg", true),
            noAnim = getBoolean("noAnim", true),
            dnd = getBoolean("dnd", true),
            scale = getInt("scale", 100),
            lowLatency = getBoolean("lowLatency", true),
            quietRadios = getBoolean("quietRadios", true),
        )
    }

    fun saveOpts(ctx: Context, o: Opts) = prefs(ctx).edit()
        .putBoolean("gameMode", o.gameMode).putBoolean("refresh", o.refresh)
        .putBoolean("killBg", o.killBg).putBoolean("noAnim", o.noAnim)
        .putBoolean("dnd", o.dnd).putInt("scale", o.scale)
        .putBoolean("lowLatency", o.lowLatency).putBoolean("quietRadios", o.quietRadios).apply()

    fun selectedGame(ctx: Context): String? = prefs(ctx).getString("game", null)
    fun selectGame(ctx: Context, pkg: String) = prefs(ctx).edit().putString("game", pkg).apply()

    // ── aplicar ─────────────────────────────────────────────────────────────
    fun apply(ctx: Context, pkg: String, o: Opts): List<Step> {
        if (isActive(ctx)) restore(ctx)
        Priv.selfGrant(ctx)
        val b = backup(ctx).edit()
        val steps = mutableListOf<Step>()
        val sh = Shell.ready

        // Rendimiento ------------------------------------------------------
        if (o.killBg) {
            if (sh) {
                val r = Shell.run("am kill-all")
                steps += Step("Apps en segundo plano cerradas", r.ok, r.err)
            } else {
                val n = killBackgroundBasic(ctx, pkg)
                steps += Step("Apps en segundo plano cerradas ($n)", true, "Modo básico: Shizuku cierra más")
            }
        }

        if (o.refresh) {
            if (sh) {
                val peak = Priv.get(ctx, Priv.Ns.SYSTEM, "peak_refresh_rate")
                val min = Priv.get(ctx, Priv.Ns.SYSTEM, "min_refresh_rate")
                if (peak != null && min != null) {
                    b.putString("peak_refresh_rate", peak).putString("min_refresh_rate", min)
                    val ok = Priv.put(ctx, Priv.Ns.SYSTEM, "peak_refresh_rate", "120.0") &&
                        Priv.put(ctx, Priv.Ns.SYSTEM, "min_refresh_rate", "120.0")
                    steps += Step("Pantalla fija a 120 Hz", ok)
                }
            } else steps += Step("Pantalla a 120 Hz", false, "Necesita Shizuku activo")
        }

        if (o.noAnim) {
            if (Priv.canWrite(ctx, Priv.Ns.GLOBAL)) {
                var ok = true
                for (k in ANIM_KEYS) {
                    val old = Priv.get(ctx, Priv.Ns.GLOBAL, k) ?: "1.0"
                    b.putString("anim_$k", old)
                    ok = Priv.put(ctx, Priv.Ns.GLOBAL, k, "0") && ok
                }
                steps += Step("Animaciones del sistema apagadas", ok)
            } else steps += Step("Sin animaciones", false, "Activa Shizuku una vez para el permiso permanente")
        }

        if (o.dnd) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (Priv.hasDndAccess(ctx)) {
                b.putInt("dnd_filter", nm.currentInterruptionFilter)
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                steps += Step("No molestar activado", true)
            } else if (sh) {
                b.putString("zen_mode", Priv.get(ctx, Priv.Ns.GLOBAL, "zen_mode") ?: "0")
                val r = Shell.run("cmd notification set_dnd priority")
                steps += Step("No molestar activado", r.ok, r.err)
            } else steps += Step("No molestar", false, "Activa Shizuku una vez para el permiso permanente")
        }

        if (o.gameMode || o.scale < 100) {
            if (sh) {
                val r = Shell.run("cmd game mode performance $pkg")
                val ok = r.ok && !r.out.contains("error", true) && !r.err.contains("error", true)
                b.putBoolean("game_mode", true)
                steps += Step("Modo rendimiento de Android", ok, if (ok) "" else "Este juego no admite el modo de Android (no pasa nada)")
            } else if (o.gameMode) steps += Step("Modo rendimiento", false, "Necesita Shizuku activo")
        }

        if (o.scale < 100) {
            if (sh) {
                b.putString("game_overlay", Shell.run("device_config get game_overlay $pkg").out.ifBlank { "null" })
                val f = String.format(Locale.US, "%.2f", o.scale / 100.0)
                val r = Shell.run("device_config put game_overlay $pkg mode=2,downscaleFactor=$f:mode=3,downscaleFactor=$f")
                Shell.run("am force-stop $pkg") // se aplica al volver a abrir el juego
                steps += Step("Resolución interna al ${o.scale}%", r.ok, if (r.ok) "" else r.err)
            } else steps += Step("Resolución del juego", false, "Necesita Shizuku activo")
        }

        // Red gamer --------------------------------------------------------
        if (o.lowLatency) {
            if (sh) {
                val r = Shell.run("cmd wifi force-low-latency-mode enabled")
                val ok = r.ok && !r.out.contains("unknown", true) && !r.err.contains("unknown", true)
                if (ok) b.putBoolean("low_latency", true)
                steps += Step("Wi-Fi en baja latencia", ok, if (ok) "" else "Tu Android no expone este modo")
            } else steps += Step("Wi-Fi en baja latencia", false, "Necesita Shizuku activo")
        }

        if (o.quietRadios) {
            var did = false
            // Ubicacion: evita que otras apps escaneen Wi-Fi durante la partida (picos de lag)
            if (sh) {
                val on = Shell.run("cmd location is-location-enabled").out.trim()
                if (on == "true" || on == "false") {
                    b.putString("location_on", on)
                    if (on == "true") Shell.run("cmd location set-location-enabled false")
                    did = true
                }
            } else if (Priv.hasWss(ctx)) {
                Priv.get(ctx, Priv.Ns.SECURE, "location_mode")?.let { old ->
                    b.putString("location_mode", old)
                    Priv.put(ctx, Priv.Ns.SECURE, "location_mode", "0")
                    did = true
                }
            }
            if (Priv.canWrite(ctx, Priv.Ns.GLOBAL)) {
                for (k in SCAN_KEYS) {
                    val old = Priv.get(ctx, Priv.Ns.GLOBAL, k) ?: continue // no se toca lo que no se puede restaurar
                    b.putString("scan_$k", old)
                    Priv.put(ctx, Priv.Ns.GLOBAL, k, "0")
                    did = true
                }
            }
            steps += Step(
                "Ubicación y búsquedas en pausa", did,
                if (did) "" else "Activa Shizuku una vez para el permiso permanente"
            )
        }

        b.putBoolean("active", true).putString("pkg", pkg).commit()
        return steps
    }

    /** Sin Shizuku: cierra lo que Android permite a una app normal. */
    private fun killBackgroundBasic(ctx: Context, game: String): Int {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val pm = ctx.packageManager
        val keep = setOf(game, ctx.packageName, "com.whatsapp", "com.whatsapp.w4b", "moe.shizuku.privileged.api")
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        var n = 0
        for (ri in pm.queryIntentActivities(launch, 0)) {
            val p = ri.activityInfo.packageName
            if (p in keep) continue
            runCatching { am.killBackgroundProcesses(p); n++ }
        }
        return n
    }

    // ── restaurar ───────────────────────────────────────────────────────────
    fun restore(ctx: Context): List<Step> {
        val bp = backup(ctx)
        if (!bp.getBoolean("active", false)) return emptyList()
        val pkg = bp.getString("pkg", null)
        val steps = mutableListOf<Step>()
        val e = bp.edit()
        val sh = Shell.ready

        fun item(name: String, keys: List<String>, action: () -> Boolean) {
            if (keys.none { bp.contains(it) }) return
            val ok = runCatching(action).getOrDefault(false)
            if (ok) keys.forEach { e.remove(it) }
            steps += Step(name, ok, if (ok) "" else "Activa Shizuku y pulsa Restaurar de nuevo")
        }

        item("Frecuencia de pantalla original", listOf("peak_refresh_rate", "min_refresh_rate")) {
            Priv.put(ctx, Priv.Ns.SYSTEM, "peak_refresh_rate", bp.getString("peak_refresh_rate", "null")!!) &&
                Priv.put(ctx, Priv.Ns.SYSTEM, "min_refresh_rate", bp.getString("min_refresh_rate", "null")!!)
        }
        item("Animaciones originales", ANIM_KEYS.map { "anim_$it" }) {
            ANIM_KEYS.all { k -> bp.getString("anim_$k", null)?.let { Priv.put(ctx, Priv.Ns.GLOBAL, k, it) } ?: true }
        }
        item("No molestar como estaba", listOf("dnd_filter")) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.setInterruptionFilter(bp.getInt("dnd_filter", NotificationManager.INTERRUPTION_FILTER_ALL))
            true
        }
        item("No molestar como estaba", listOf("zen_mode")) {
            val mode = when (bp.getString("zen_mode", "0")) { "1" -> "priority"; "2" -> "none"; "3" -> "alarms"; else -> "off" }
            sh && Shell.run("cmd notification set_dnd $mode").ok
        }
        if (pkg != null) {
            item("Resolución original del juego", listOf("game_overlay")) {
                val old = bp.getString("game_overlay", "null")!!
                sh && (if (old == "null") Shell.run("device_config delete game_overlay $pkg")
                else Shell.run("device_config put game_overlay $pkg $old")).ok
            }
            item("Modo de juego estándar", listOf("game_mode")) {
                sh && Shell.run("cmd game mode standard $pkg").let { true }
            }
        }
        item("Wi-Fi en modo normal", listOf("low_latency")) {
            sh && Shell.run("cmd wifi force-low-latency-mode disabled").ok
        }
        item("Ubicación como estaba", listOf("location_on")) {
            sh && (bp.getString("location_on", "true") != "true" ||
                Shell.run("cmd location set-location-enabled true").ok)
        }
        item("Ubicación como estaba", listOf("location_mode")) {
            Priv.put(ctx, Priv.Ns.SECURE, "location_mode", bp.getString("location_mode", "3")!!)
        }
        item("Búsquedas de redes como estaban", SCAN_KEYS.map { "scan_$it" }) {
            SCAN_KEYS.all { k -> bp.getString("scan_$k", null)?.let { Priv.put(ctx, Priv.Ns.GLOBAL, k, it) } ?: true }
        }

        e.commit()
        // Si todo volvio, se cierra la sesion; si quedo algo pendiente, se conserva para reintentar.
        val left = bp.all.keys - setOf("active", "pkg")
        if (left.isEmpty()) bp.edit().clear().commit()
        return steps
    }

    fun pendingRestore(ctx: Context): Boolean =
        isActive(ctx) && (backup(ctx).all.keys - setOf("active", "pkg")).isNotEmpty()
}

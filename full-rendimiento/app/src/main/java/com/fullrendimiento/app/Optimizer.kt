package com.fullrendimiento.app

import android.content.Context
import android.content.SharedPreferences

/** Opciones elegidas por el usuario. */
data class Opts(
    val gameMode: Boolean = true,
    val refresh: Boolean = true,
    val killBg: Boolean = true,
    val noAnim: Boolean = true,
    val dnd: Boolean = true,
    val scale: Int = 100,          // resolucion interna del juego: 100, 90, 80 o 70 %
)

/** Resultado de cada paso, para mostrarlo en pantalla. */
data class Step(val name: String, val ok: Boolean, val detail: String = "")

/**
 * Aplica y revierte las optimizaciones. Antes de cambiar algo guarda el valor original,
 * asi "Restaurar" deja el celular exactamente como estaba.
 */
object Optimizer {

    private const val BACKUP = "backup"
    private const val SETTINGS = "settings"
    private val ANIM_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")

    private fun backup(ctx: Context): SharedPreferences = ctx.getSharedPreferences(BACKUP, Context.MODE_PRIVATE)
    private fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

    fun isActive(ctx: Context) = backup(ctx).getBoolean("active", false)
    fun activePkg(ctx: Context): String? = backup(ctx).getString("pkg", null)

    // ── preferencias de la interfaz ─────────────────────────────────────────
    fun loadOpts(ctx: Context): Opts = prefs(ctx).run {
        Opts(
            gameMode = getBoolean("gameMode", true),
            refresh = getBoolean("refresh", true),
            killBg = getBoolean("killBg", true),
            noAnim = getBoolean("noAnim", true),
            dnd = getBoolean("dnd", true),
            scale = getInt("scale", 100),
        )
    }

    fun saveOpts(ctx: Context, o: Opts) = prefs(ctx).edit()
        .putBoolean("gameMode", o.gameMode).putBoolean("refresh", o.refresh)
        .putBoolean("killBg", o.killBg).putBoolean("noAnim", o.noAnim)
        .putBoolean("dnd", o.dnd).putInt("scale", o.scale).apply()

    fun selectedGame(ctx: Context): String? = prefs(ctx).getString("game", null)
    fun selectGame(ctx: Context, pkg: String) = prefs(ctx).edit().putString("game", pkg).apply()

    // ── aplicar ─────────────────────────────────────────────────────────────
    private fun get(ns: String, key: String): String =
        Shell.run("settings get $ns $key").out.ifBlank { "null" }

    private fun put(ns: String, key: String, value: String): Shell.Result =
        if (value == "null") Shell.run("settings delete $ns $key")
        else Shell.run("settings put $ns $key $value")

    fun apply(ctx: Context, pkg: String, o: Opts): List<Step> {
        if (isActive(ctx)) restore(ctx)
        val b = backup(ctx).edit()
        val steps = mutableListOf<Step>()

        if (o.killBg) {
            val r = Shell.run("am kill-all")
            steps += Step("Apps en segundo plano cerradas", r.ok, r.err)
        }

        if (o.refresh) {
            b.putString("peak_refresh_rate", get("system", "peak_refresh_rate"))
            b.putString("min_refresh_rate", get("system", "min_refresh_rate"))
            val r1 = put("system", "peak_refresh_rate", "120.0")
            val r2 = put("system", "min_refresh_rate", "120.0")
            steps += Step("Pantalla fija a 120 Hz", r1.ok && r2.ok, r1.err.ifBlank { r2.err })
        }

        if (o.noAnim) {
            var ok = true
            for (k in ANIM_KEYS) {
                b.putString("anim_$k", get("global", k))
                ok = put("global", k, "0") .ok && ok
            }
            steps += Step("Animaciones del sistema apagadas", ok)
        }

        if (o.dnd) {
            b.putString("zen_mode", get("global", "zen_mode"))
            val r = Shell.run("cmd notification set_dnd priority")
            steps += Step("No molestar activado", r.ok, r.err)
        }

        if (o.gameMode || o.scale < 100) {
            val r = Shell.run("cmd game mode performance $pkg")
            val ok = r.ok && !r.out.contains("error", true) && !r.err.contains("error", true)
            b.putBoolean("game_mode", true)
            steps += Step(
                "Modo rendimiento de Android", ok,
                if (ok) "" else "Este juego no admite el modo de Android (no pasa nada)"
            )
        }

        if (o.scale < 100) {
            val old = Shell.run("device_config get game_overlay $pkg").out.ifBlank { "null" }
            b.putString("game_overlay", old)
            val f = String.format(java.util.Locale.US, "%.2f", o.scale / 100.0)
            val r = Shell.run("device_config put game_overlay $pkg mode=2,downscaleFactor=$f:mode=3,downscaleFactor=$f")
            Shell.run("am force-stop $pkg") // la resolucion se aplica al volver a abrir el juego
            steps += Step("Resolución interna al ${o.scale}%", r.ok, if (r.ok) "" else r.err)
        }

        b.putBoolean("active", true).putString("pkg", pkg).commit()
        return steps
    }

    // ── restaurar ───────────────────────────────────────────────────────────
    fun restore(ctx: Context): List<Step> {
        val bp = backup(ctx)
        if (!bp.getBoolean("active", false)) return emptyList()
        // Sin Shizuku no se puede revertir: se conserva la copia para intentarlo despues.
        if (!Shell.ready) return listOf(Step("Activa Shizuku para restaurar", false))
        val pkg = bp.getString("pkg", null)
        val steps = mutableListOf<Step>()

        if (bp.contains("peak_refresh_rate")) {
            val r1 = put("system", "peak_refresh_rate", bp.getString("peak_refresh_rate", "null")!!)
            val r2 = put("system", "min_refresh_rate", bp.getString("min_refresh_rate", "null")!!)
            steps += Step("Frecuencia de pantalla original", r1.ok && r2.ok)
        }
        if (ANIM_KEYS.any { bp.contains("anim_$it") }) {
            var ok = true
            for (k in ANIM_KEYS) bp.getString("anim_$k", null)?.let { ok = put("global", k, it).ok && ok }
            steps += Step("Animaciones originales", ok)
        }
        if (bp.contains("zen_mode")) {
            val mode = when (bp.getString("zen_mode", "0")) {
                "1" -> "priority"; "2" -> "none"; "3" -> "alarms"; else -> "off"
            }
            steps += Step("No molestar como estaba", Shell.run("cmd notification set_dnd $mode").ok)
        }
        if (pkg != null && bp.contains("game_overlay")) {
            val old = bp.getString("game_overlay", "null")!!
            val r = if (old == "null") Shell.run("device_config delete game_overlay $pkg")
            else Shell.run("device_config put game_overlay $pkg $old")
            steps += Step("Resolución original del juego", r.ok)
        }
        if (pkg != null && bp.getBoolean("game_mode", false)) {
            Shell.run("cmd game mode standard $pkg")
            steps += Step("Modo de juego estándar", true)
        }

        bp.edit().clear().commit()
        return steps
    }
}

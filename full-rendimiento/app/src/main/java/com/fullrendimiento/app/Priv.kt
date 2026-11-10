package com.fullrendimiento.app

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

/**
 * Capa de privilegios. Cada ajuste usa el mejor metodo disponible:
 *
 *  1. Shizuku activo           → shell con permisos de ADB (todo).
 *  2. Permiso permanente (WSS) → WRITE_SECURE_SETTINGS, que la app se auto-otorga la primera vez
 *                                que Shizuku esta activo y que NO se pierde al reiniciar.
 *  3. Sin nada                 → solo lo que una app normal puede hacer.
 */
object Priv {

    enum class Ns { GLOBAL, SECURE, SYSTEM }

    enum class Level { FULL, PERMANENT, BASIC }

    private const val WSS = Manifest.permission.WRITE_SECURE_SETTINGS

    fun hasWss(ctx: Context) = ctx.checkSelfPermission(WSS) == PackageManager.PERMISSION_GRANTED

    fun hasDndAccess(ctx: Context) =
        ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun batteryExempt(ctx: Context) =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun level(ctx: Context): Level = when {
        Shell.ready -> Level.FULL
        hasWss(ctx) -> Level.PERMANENT
        else -> Level.BASIC
    }

    /**
     * Con Shizuku activo, la app se otorga permisos que sobreviven a los reinicios:
     * ajustes seguros, acceso a No molestar, acceso de uso (detectar el juego), notificaciones
     * y exencion de ahorro de bateria.
     * Devuelve true si algo cambio.
     */
    fun selfGrant(ctx: Context): Boolean {
        if (!Shell.ready) return false
        val p = ctx.packageName
        var changed = false
        if (!hasWss(ctx)) {
            Shell.run("pm grant $p $WSS"); changed = true
        }
        if (!hasDndAccess(ctx)) {
            Shell.run("cmd notification allow_dnd $p"); changed = true
        }
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Shell.run("pm grant $p ${Manifest.permission.POST_NOTIFICATIONS}"); changed = true
        }
        if (!hasUsageAccess(ctx)) {
            Shell.run("appops set $p GET_USAGE_STATS allow"); changed = true
        }
        if (!batteryExempt(ctx)) {
            Shell.run("cmd deviceidle whitelist +$p"); changed = true
        }
        return changed
    }

    // ── ajustes del sistema ─────────────────────────────────────────────────
    private fun nsName(ns: Ns) = ns.name.lowercase()

    /** Lee un ajuste. Devuelve "null" si no existe, o null si no se pudo leer. */
    fun get(ctx: Context, ns: Ns, key: String): String? {
        if (Shell.ready) {
            val r = Shell.run("settings get ${nsName(ns)} $key")
            if (r.ok) return r.out.ifBlank { "null" }
        }
        return try {
            val cr = ctx.contentResolver
            when (ns) {
                Ns.GLOBAL -> Settings.Global.getString(cr, key)
                Ns.SECURE -> Settings.Secure.getString(cr, key)
                Ns.SYSTEM -> Settings.System.getString(cr, key)
            } ?: "null"
        } catch (e: Throwable) {
            null // Android 12+ bloquea leer algunos ajustes ocultos sin Shizuku
        }
    }

    /** Escribe un ajuste ("null" = borrarlo). */
    fun put(ctx: Context, ns: Ns, key: String, value: String): Boolean {
        if (Shell.ready) {
            val cmd = if (value == "null") "settings delete ${nsName(ns)} $key"
            else "settings put ${nsName(ns)} $key $value"
            if (Shell.run(cmd).ok) return true
        }
        if (ns == Ns.SYSTEM || !hasWss(ctx)) return false // los ocultos de "system" solo con Shizuku
        return try {
            val cr = ctx.contentResolver
            val v = if (value == "null") null else value
            when (ns) {
                Ns.GLOBAL -> Settings.Global.putString(cr, key, v)
                Ns.SECURE -> Settings.Secure.putString(cr, key, v)
                else -> false
            }
        } catch (e: Throwable) {
            false
        }
    }

    fun canWrite(ctx: Context, ns: Ns) = Shell.ready || (ns != Ns.SYSTEM && hasWss(ctx))
}

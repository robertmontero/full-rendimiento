package com.fullrendimiento.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano mientras dura la sesion de juego:
 *  - muestra la temperatura en la notificacion (y avisa si se calienta),
 *  - detecta cuando cierras el juego y restaura todo automaticamente,
 *  - boton "Restaurar" en la notificacion.
 */
class GameService : Service() {

    companion object {
        private const val CHANNEL = "session"
        private const val CHANNEL_ALERT = "alerts"
        private const val NOTIF_ID = 7
        private const val ACTION_START = "start"
        private const val ACTION_RESTORE = "restore"
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_NAME = "name"

        fun start(ctx: Context, pkg: String, name: String) {
            val i = Intent(ctx, GameService::class.java)
                .setAction(ACTION_START).putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_NAME, name)
            ctx.startForegroundService(i)
        }

        fun restore(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, GameService::class.java).setAction(ACTION_RESTORE))
        }

        fun batteryTemp(ctx: Context): Float? {
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
            val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            return if (t == Int.MIN_VALUE) null else t / 10f
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitor: Job? = null
    private var gameName = ""
    private var hotWarned = false
    private var lastPing: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sesión de juego", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Avisos de temperatura", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESTORE -> {
                goForeground("Restaurando ajustes…")
                monitor?.cancel()
                scope.launch {
                    Optimizer.restore(this@GameService)
                    finish()
                }
            }
            ACTION_START -> {
                val pkg = intent.getStringExtra(EXTRA_PKG) ?: return START_NOT_STICKY
                gameName = intent.getStringExtra(EXTRA_NAME) ?: pkg
                hotWarned = false
                goForeground(text(batteryTemp(this)))
                monitor?.cancel()
                monitor = scope.launch { watch(pkg) }
            }
            else -> {
                // Reinicio del sistema sin datos: si quedo una sesion activa, seguir vigilando
                val pkg = Optimizer.activePkg(this)
                if (pkg == null) {
                    stopSelf()
                } else {
                    goForeground(text(batteryTemp(this)))
                    monitor = scope.launch { watch(pkg) }
                }
            }
        }
        return START_STICKY
    }

    private suspend fun watch(pkg: String) {
        var seen = false
        var missing = 0
        var waited = 0
        while (scope.isActive) {
            delay(3000)
            waited += 3
            val temp = batteryTemp(this)
            lastPing = Net.quickPing()
            update(text(temp))
            if (temp != null && temp >= 42f && !hotWarned) {
                hotWarned = true
                alert("🔥 Tu celular está caliente (${temp.toInt()}°C)", "Baja la calidad gráfica o quita la funda para mantener los FPS.")
            } else if (temp != null && temp < 39f) {
                hotWarned = false
            }
            // Con Shizuku: ¿sigue vivo el proceso? Sin Shizuku: ¿esta el juego en pantalla?
            val viaShell = Shell.ready
            val running: Boolean = when {
                viaShell -> Shell.run("pidof $pkg").out.isNotBlank()
                Priv.hasUsageAccess(this) -> foregroundApp() == pkg
                else -> continue
            }
            when {
                running -> { seen = true; missing = 0 }
                seen -> missing++
                waited > 120 -> missing = 99 // el juego nunca se abrio
            }
            // Sin Shizuku se espera mas (~30 s) para no restaurar si solo cambiaste de app un momento
            if (missing >= (if (viaShell) 2 else 10)) {
                Optimizer.restore(this)
                if (Optimizer.pendingRestore(this)) {
                    alert("⚠️ Falta restaurar algo", "Activa Shizuku y abre Full Rendimiento para terminar.")
                } else {
                    alert("✅ Juego cerrado", "Todo volvió a la normalidad.")
                }
                finish()
                return
            }
        }
    }

    /** App que esta en pantalla ahora (requiere acceso de uso). */
    private fun foregroundApp(): String? {
        val usm = getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 60 * 60 * 1000L, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
        }
        return last
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun text(temp: Float?): String =
        "$gameName · " + (temp?.let { "${it.toInt()}°C" } ?: "—°C") +
            " · ping " + (lastPing?.let { "$it ms" } ?: "—")

    private fun build(content: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val restore = PendingIntent.getService(
            this, 1, Intent(this, GameService::class.java).setAction(ACTION_RESTORE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_bolt)
            .setColor(0xFF7C5CFF.toInt())
            .setContentTitle("⚡ Full rendimiento activo")
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Restaurar", restore)
            .build()
    }

    private fun goForeground(content: String) {
        ServiceCompat.startForeground(
            this, NOTIF_ID, build(content), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun update(content: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(content))
    }

    private fun alert(title: String, body: String) {
        val n = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_bolt)
            .setColor(0xFF7C5CFF.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(8, n)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

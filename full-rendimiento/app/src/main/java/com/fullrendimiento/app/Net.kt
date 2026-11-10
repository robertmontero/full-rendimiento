package com.fullrendimiento.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import kotlin.math.abs

/** Diagnostico de red y DNS privado. */
object Net {

    data class WifiState(
        val freqMhz: Int,
        val rssi: Int,
        val linkMbps: Int,
        val standard: String,
    ) {
        val band: String
            get() = when {
                freqMhz >= 5925 -> "6 GHz"
                freqMhz >= 4900 -> "5 GHz"
                freqMhz > 0 -> "2.4 GHz"
                else -> "—"
            }
        val signal: String
            get() = when {
                rssi >= -55 -> "Excelente"
                rssi >= -65 -> "Buena"
                rssi >= -72 -> "Regular"
                else -> "Débil"
            }
    }

    data class Ping(val avg: Double, val min: Double, val max: Double, val jitter: Double, val loss: Int) {
        val label: String get() = if (loss >= 100) "sin respuesta" else "${avg.toInt()} ms"
    }

    data class Report(
        val wifi: WifiState?,
        val mobile: Boolean,
        val router: Ping?,
        val internet: Ping?,
        val dnsMs: Long?,
        val web: Boolean,
        val verdict: String,
        val color: Int,          // 0 excelente, 1 bueno, 2 inestable, 3 malo
        val tips: List<String>,
    )

    // ── estado del Wi-Fi ────────────────────────────────────────────────────
    fun wifi(ctx: Context): WifiState? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        @Suppress("DEPRECATION")
        val wi = (caps.transportInfo as? WifiInfo)
            ?: ctx.applicationContext.getSystemService(WifiManager::class.java).connectionInfo
            ?: return WifiState(0, -100, 0, "—")
        val std = when (wi.wifiStandard) {
            ScanResult.WIFI_STANDARD_LEGACY -> "Wi-Fi 1-3"
            ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4"
            ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5"
            ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6"
            8 -> "Wi-Fi 7" // WIFI_STANDARD_11BE (Android 13+)
            else -> "—"
        }
        return WifiState(wi.frequency, wi.rssi, maxOf(wi.linkSpeed, wi.rxLinkSpeedMbps), std)
    }

    fun onMobile(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    fun gateway(ctx: Context): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
    }

    // ── ping ────────────────────────────────────────────────────────────────
    /** Ping con el binario del sistema (funciona sin root). */
    fun ping(host: String, count: Int = 12): Ping? = try {
        val p = ProcessBuilder("/system/bin/ping", "-c", "$count", "-i", "0.2", "-W", "1", host)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        val times = Regex("time=([0-9.]+)").findAll(out).map { it.groupValues[1].toDouble() }.toList()
        if (times.isEmpty()) Ping(0.0, 0.0, 0.0, 0.0, 100)
        else {
            val jitter = if (times.size > 1) times.zipWithNext { a, b -> abs(b - a) }.average() else 0.0
            Ping(times.average(), times.min(), times.max(), jitter, ((count - times.size) * 100) / count)
        }
    } catch (e: Throwable) {
        null
    }

    /** Un solo ping rapido (para la notificacion durante el juego). */
    fun quickPing(host: String = "1.1.1.1"): Int? = try {
        val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "1", host).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        Regex("time=([0-9.]+)").find(out)?.groupValues?.get(1)?.toDouble()?.toInt()
    } catch (e: Throwable) {
        null
    }

    private fun dnsTime(): Long? = try {
        val t = System.nanoTime()
        InetAddress.getAllByName("x${System.nanoTime() % 100000}.speed.cloudflare.com")
        (System.nanoTime() - t) / 1_000_000
    } catch (e: Throwable) {
        try {
            val t = System.nanoTime()
            InetAddress.getAllByName("www.google.com")
            (System.nanoTime() - t) / 1_000_000
        } catch (e2: Throwable) {
            null
        }
    }

    /** Comprueba que hay internet real (sirve tambien para validar el DNS privado). */
    fun webOk(): Boolean = try {
        val c = URL("https://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection
        c.connectTimeout = 4000
        c.readTimeout = 4000
        c.useCaches = false
        val ok = c.responseCode == 204
        c.disconnect()
        ok
    } catch (e: Throwable) {
        false
    }

    // ── prueba completa ─────────────────────────────────────────────────────
    fun test(ctx: Context): Report {
        val w = wifi(ctx)
        val mobile = onMobile(ctx)
        val gw = gateway(ctx)
        val router = if (w != null && gw != null) ping(gw) else null
        val inet = ping("1.1.1.1") ?: ping("8.8.8.8")
        val dns = dnsTime()
        val web = webOk()

        val tips = mutableListOf<String>()
        var score = 0
        fun bad(level: Int, tip: String) { score = maxOf(score, level); tips += tip }

        if (!web) bad(3, "No hay salida a internet. Si activaste un DNS privado, vuelve a “Automático”.")
        if (w != null) {
            if (w.band == "2.4 GHz") bad(1, "Estás en 2.4 GHz: más interferencia. Si tu router tiene 5 GHz, conéctate a esa red.")
            if (w.rssi < -72) bad(2, "Señal débil (${w.rssi} dBm): acércate al router o quita obstáculos.")
            else if (w.rssi < -65) bad(1, "Señal regular (${w.rssi} dBm): juega más cerca del router.")
        }
        router?.let {
            if (it.loss > 0) bad(2, "Se pierden paquetes entre el celular y el router (${it.loss}%): problema de señal Wi-Fi.")
            if (it.jitter > 15) bad(2, "El Wi-Fi da saltos (${it.jitter.toInt()} ms): interferencia o el router está saturado.")
            else if (it.avg > 12) bad(1, "Respuesta lenta del router (${it.avg.toInt()} ms): señal o router ocupado.")
        }
        inet?.let {
            if (it.loss >= 100) bad(3, "Internet no responde al ping.")
            else {
                if (it.loss >= 5) bad(2, "Pérdida de paquetes hacia internet (${it.loss}%): revisa tu proveedor o reinicia el router.")
                if (it.jitter > 20) bad(2, "Ping inestable (saltos de ${it.jitter.toInt()} ms): alguien está descargando o viendo video en tu red.")
                if (it.avg > 80) bad(1, "Ping alto hacia internet (${it.avg.toInt()} ms).")
            }
        }
        if (dns != null && dns > 150) bad(1, "El DNS responde lento (${dns} ms): prueba Cloudflare o Google en “DNS rápido”.")
        if (dns == null && web) bad(1, "El DNS falló: activa Cloudflare o Google en “DNS rápido”.")
        if (mobile && w == null) tips += "Estás con datos móviles. El ping suele ser más estable en Wi-Fi de 5 GHz."
        if (tips.isEmpty()) tips += "Tu red está lista para jugar. Si el lag es solo en un juego, revisa que esté en el servidor de tu región."

        val verdict = when (score) { 0 -> "Excelente"; 1 -> "Buena"; 2 -> "Inestable"; else -> "Mala" }
        return Report(w, mobile, router, inet, dns, web, verdict, score, tips)
    }

    // ── DNS privado ─────────────────────────────────────────────────────────
    enum class Dns(val label: String, val host: String?) {
        AUTO("Automático", null),
        CLOUDFLARE("Cloudflare", "one.one.one.one"),
        GOOGLE("Google", "dns.google"),
    }

    fun currentDns(ctx: Context): Dns? {
        val mode = Priv.get(ctx, Priv.Ns.GLOBAL, "private_dns_mode") ?: return null
        if (mode != "hostname") return Dns.AUTO
        val host = Priv.get(ctx, Priv.Ns.GLOBAL, "private_dns_specifier") ?: return null
        return Dns.entries.firstOrNull { it.host == host } ?: Dns.AUTO
    }

    fun canSetDns(ctx: Context) = Priv.canWrite(ctx, Priv.Ns.GLOBAL)

    /**
     * Cambia el DNS privado y comprueba que siga habiendo internet.
     * Si la red bloquea el DNS privado, vuelve a "Automático" para no dejarte sin conexion.
     */
    fun setDns(ctx: Context, d: Dns): Boolean {
        val ok = if (d.host == null) {
            Priv.put(ctx, Priv.Ns.GLOBAL, "private_dns_mode", "opportunistic")
        } else {
            Priv.put(ctx, Priv.Ns.GLOBAL, "private_dns_specifier", d.host) &&
                Priv.put(ctx, Priv.Ns.GLOBAL, "private_dns_mode", "hostname")
        }
        if (!ok) return false
        if (d.host == null) return true
        for (i in 0 until 4) {
            Thread.sleep(1500)
            if (webOk()) return true
        }
        Priv.put(ctx, Priv.Ns.GLOBAL, "private_dns_mode", "opportunistic")
        return false
    }
}

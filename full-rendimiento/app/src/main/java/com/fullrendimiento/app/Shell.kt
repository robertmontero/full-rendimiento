package com.fullrendimiento.app

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Ejecuta comandos con permisos de ADB a traves de Shizuku (sin root).
 */
object Shell {

    data class Result(val code: Int, val out: String, val err: String) {
        val ok: Boolean get() = code == 0
    }

    enum class State { NOT_RUNNING, NO_PERMISSION, READY }

    fun state(): State = try {
        when {
            !Shizuku.pingBinder() -> State.NOT_RUNNING
            Shizuku.isPreV11() -> State.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> State.NO_PERMISSION
            else -> State.READY
        }
    } catch (e: Throwable) {
        State.NOT_RUNNING
    }

    val ready: Boolean get() = state() == State.READY

    // Shizuku.newProcess es privado desde la API 13; se usa por reflexion (metodo estable y muy usado).
    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
    }

    /** Ejecuta [cmd] en una shell con permisos de ADB. Llamar desde un hilo de fondo. */
    fun run(cmd: String): Result {
        if (!ready) return Result(-1, "", "Shizuku no está activo")
        return try {
            val p = newProcess.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val out = StringBuilder()
            val err = StringBuilder()
            val tOut = Thread { runCatching { out.append(p.inputStream.bufferedReader().readText()) } }
            val tErr = Thread { runCatching { err.append(p.errorStream.bufferedReader().readText()) } }
            tOut.start()
            tErr.start()
            val code = p.waitFor()
            tOut.join(2000)
            tErr.join(2000)
            Result(code, out.toString().trim(), err.toString().trim())
        } catch (e: Throwable) {
            Result(-1, "", (e.cause ?: e).message ?: "error")
        }
    }
}

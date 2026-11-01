# ⚡ Full Rendimiento

App para el **Samsung Galaxy A35** que prepara el celular para jugar con el máximo rendimiento
y lo deja todo como estaba al cerrar el juego. Sin root: usa **Shizuku**.

## Qué hace (todo reversible)

| Optimización | Cómo |
|---|---|
| 🚀 Modo rendimiento | `cmd game mode performance <juego>` (Game Mode de Android) |
| 🧹 Cerrar apps de fondo | `am kill-all` (solo procesos en segundo plano) |
| 🖥️ Pantalla a 120 Hz | `peak_refresh_rate` / `min_refresh_rate` = 120 |
| 🌀 Sin animaciones | escalas de animación a 0 |
| 🔕 No molestar | `cmd notification set_dnd priority` |
| 🎯 Resolución del juego | `device_config game_overlay` (100/90/80/70 %) |

- Guarda los valores originales antes de cambiar nada.
- Una notificación muestra la temperatura, avisa si pasa de 42 °C y tiene un botón **Restaurar**.
- Al cerrar el juego, restaura todo automáticamente.

**Límites honestos:** ninguna app sin root puede asignar más RAM o "memoria virtual" a un juego,
subir el procesador por encima de lo que Samsung permite, ni bajar el ping (depende de internet).

## Uso
1. Instala **Shizuku** (Play Store) y pulsa *Iniciar* vía Depuración inalámbrica.
2. Abre Full Rendimiento, da permiso, elige tu juego y pulsa **⚡ FULL RENDIMIENTO**.
3. Tras reiniciar el celular, vuelve a iniciar Shizuku.

## Compilar
- **GitHub:** sube esta carpeta a un repositorio; la pestaña *Actions* genera `full-rendimiento.apk`.
- **Android Studio:** abre la carpeta y usa *Build › Build APK(s)*.
- **Terminal:** `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.

La firma está en `keystore/` (contraseña `fullrendimiento`). Úsala siempre para poder actualizar
la app sin desinstalarla.

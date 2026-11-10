# ⚡ Full Rendimiento

**Creado por Odiseo.co** · Versión 2.0

App para el **Samsung Galaxy A35** que prepara el celular para jugar con el máximo rendimiento y
la mejor red, y lo deja todo como estaba al cerrar el juego. Sin root.

## Niveles de acceso

| Nivel | Cómo se consigue | Qué permite |
|---|---|---|
| 🟢 **Completo** | Shizuku activo | Todo |
| 🟡 **Permanente** | Activar Shizuku **una sola vez**: la app se otorga permisos que no se pierden al reiniciar | Casi todo, sin volver a usar Shizuku |
| 🔑 Básico | Recién instalada | Cerrar apps de fondo (modo básico) y medidor de red |

## Rendimiento

| Optimización | Requiere |
|---|---|
| 🚀 Modo rendimiento (`cmd game mode performance`) | Shizuku |
| 🧹 Cerrar apps de fondo | Nada (con Shizuku cierra más) |
| 🖥️ Pantalla a 120 Hz | Shizuku |
| 🌀 Sin animaciones | Permanente |
| 🔕 No molestar | Permanente |
| 🎯 Resolución interna del juego (100/90/80/70 %) | Shizuku |

## Red gamer

| Función | Requiere |
|---|---|
| 📡 Wi-Fi en baja latencia (sin ahorro de energía del Wi-Fi) | Shizuku |
| 🛰️ Pausar ubicación y búsquedas de redes (menos picos de ping) | Permanente |
| 🌐 DNS rápido: Cloudflare / Google, con verificación automática | Permanente |
| 📊 Medidor: banda, señal, enlace, ping al router e internet, saltos, pérdida, DNS | Nada |

- Guarda cada valor original antes de cambiarlo y lo restaura al cerrar el juego.
- La notificación muestra temperatura y ping en vivo, avisa si pasa de 42 °C y tiene **Restaurar**.
- El DNS rápido queda fijo hasta que lo cambies (sirve para las descargas del juego por Wi-Fi).

**Límites honestos:** ninguna app sin root puede añadir RAM, subir el procesador por encima de lo que
Samsung permite, bajar el ping por debajo de lo que da tu internet ni evitar el lag de otros jugadores.

## Uso
1. Instala **Shizuku** (Play Store), pulsa *Iniciar* vía Depuración inalámbrica y da permiso. Solo una vez.
2. Elige tu juego y pulsa **⚡ FULL RENDIMIENTO**.

## Compilar
GitHub Actions genera `full-rendimiento.apk` en cada cambio (pestaña *Actions › Artifacts*).
La firma está en `keystore/`: úsala siempre para actualizar sin desinstalar.

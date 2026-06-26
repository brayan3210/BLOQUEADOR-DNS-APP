# Cómo quitar la protección (recuperación)

Este documento explica, en lenguaje claro, **cómo desactivar o desinstalar el
Filtro de Contenido** si algún día lo decides **en frío** (no por impulso).

> Filosofía: que sea **difícil**, no **imposible**. Nunca quedas atrapado para
> siempre. Pero ninguna de estas vías es "un toque": requieren pasos
> deliberados, justo para que un impulso no las haga por ti.

---

## ⭐ RECOMENDACIÓN PARA TU CASO

Como **no siempre tendrás un PC a mano** y el ADB no te resulta cómodo, lo mejor
para ti es una de estas dos opciones (elige UNA):

### Opción A — La contraseña la guarda una persona de confianza (la mejor)
En vez de borrar la contraseña, **que la escriba un familiar/amigo de confianza
y NO te la diga**. Para quitar la protección le pides la contraseña a esa
persona y la metes en *"Desactivar protección"*. 
- ✅ Nunca necesitas PC ni Modo Seguro ni nada técnico.
- ✅ A prueba de impulsos (tú no la sabes).
- ✅ Es justo el modelo que recomienda la app.

### Opción B — Borras la contraseña + solo capas 1 y 2 (sin Device Owner)
Si prefieres no depender de nadie y destruir la contraseña:
- **NO actives Device Owner.** (Con Device Owner, la única salida sin PC sería
  *reset de fábrica*, que borra todo el teléfono.)
- Tu salida sin PC y sin contraseña será el **Modo Seguro** (ver más abajo).
- ⚠️ Es autónoma, pero el Modo Seguro es una vía conocida: alguien decidido
  podría usarla en un mal momento. Aun así, es bastante incómoda.

> En resumen: **Opción A si tienes a alguien de confianza. Opción B si quieres
> total autonomía sin PC.** En ambas, deja Device Owner **desactivado**.

---

## Vías para quitar la protección (de la más fácil a la más drástica)

### 1) Con la contraseña (la vía normal)
Abre la app → **Desactivar protección** → escribe la contraseña.
Esto detiene la VPN, quita el administrador de dispositivo y te da **5 minutos**
para desinstalar libremente (mantén pulsado el ícono → Desinstalar).

### 2) Modo Seguro — SIN PC y SIN contraseña ✅ (tu plan B)
En Modo Seguro, Android **desactiva las apps de terceros**, así que el guardián
de accesibilidad y la VPN **no corren** y no pueden frenarte.

1. Mantén pulsado el **botón de encendido**.
2. Mantén pulsado **"Apagar"** hasta que aparezca **"Reiniciar en modo seguro"**
   → toca **Aceptar**.
   - *(Si tu marca no muestra eso: apaga el teléfono; al encenderlo, en cuanto
     aparezca el logo, mantén pulsado **Bajar volumen** hasta que arranque. Verás
     "Modo seguro" en una esquina.)*
3. Ve a **Ajustes → Seguridad → Apps de administración del dispositivo**
   (puede llamarse "Administradores del dispositivo") → **desactiva "Filtro de
   Contenido"**.
4. Ve a **Ajustes → Aplicaciones → Filtro de Contenido → Desinstalar**
   (o mantén pulsado el ícono → Desinstalar).
5. Reinicia el teléfono con normalidad.

> ⚠️ El Modo Seguro **NO funciona si activaste Device Owner** (capa 3). Por eso,
> si quieres poder salir sin PC, **no actives Device Owner**.

### 3) ADB desde un PC — SIN contraseña (necesita computadora)
Si tienes tu PC a mano:
1. Activa **Depuración USB** en el teléfono (Ajustes → Opciones de desarrollador).
2. Conéctalo por cable y ejecuta `device-owner\quitar_device_owner.bat`
   (o el comando `adb shell dpm remove-active-admin com.brayan.filtrocontenido/.admin.FilterDeviceAdminReceiver`).
3. Desinstala la app normalmente.
- Esta es la única vía limpia para quitar **Device Owner** sin borrar el teléfono.

### 4) Restablecimiento de fábrica — SIN PC y SIN contraseña (lo nuclear) 💣
Funciona **siempre**, incluso con Device Owner, pero **borra TODO el teléfono**.
Ajustes → Sistema → Opciones de restablecimiento → **Borrar todos los datos**.
Úsalo solo como último recurso.

---

## Tabla rápida

| Vía | ¿PC? | ¿Contraseña? | ¿Funciona con Device Owner? |
|-----|:----:|:------------:|:---------------------------:|
| Desactivar en la app | No | **Sí** | Sí |
| Modo Seguro | No | No | **No** |
| ADB (`quitar` script) | **Sí** | No | Sí |
| Reset de fábrica | No | No | Sí (borra todo) |

---

## Si destruyes la contraseña (estilo PC)
- El botón "Desactivar" y "Cambiar contraseña" quedan inservibles (piden la
  contraseña vieja).
- Tus salidas pasan a ser: **Modo Seguro**, **ADB** o **reset de fábrica**.
- Por eso la recomendación: **destruir la contraseña SOLO con capas 1+2**
  (sin Device Owner), para conservar el Modo Seguro como salida sin PC.

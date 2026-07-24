# Filtro de Contenido — Android (Bloqueador DNS)

Versión Android del [Bloqueador DNS nativo](https://github.com/brayan3210/Bloqueador-DNS-NATIVO)
para PC. Mismo objetivo y mismo funcionamiento: **bloquear contenido para
adultos a nivel de DNS** como herramienta de **autocontrol**, sin SafeSearch y
sin descifrar tráfico.

> No es para dañar el dispositivo. Es para erradicar la adicción a la pornografía.

---

## 📲 Instalar (rápido)
1. Descarga **[`releases/FiltroContenido.apk`](releases/FiltroContenido.apk)** y pásalo al teléfono
   (cable USB, o envíatelo por WhatsApp/Drive/correo).
2. En el teléfono, **toca el APK** → permite "instalar apps de orígenes desconocidos" → Instalar.
3. Abre la app y sigue el [primer uso](#primer-uso).

---

## 🎨 Interfaz (rediseño — solo front)

La app estrena una interfaz tipo **dashboard** oscura (con modo claro), en línea
con un panel de seguridad moderno. **Solo cambió la capa visual**: el filtro DNS,
el guardián de accesibilidad, el bloqueo de búsquedas y las 3 capas de resistencia
funcionan **exactamente igual** que antes.

Novedades de UI:
- **Navegación por 5 pestañas** (barra inferior):
  - **Inicio** — tarjeta de estado, métricas en vivo, botón *Activar protección* y refuerzo.
  - **Actividad** — total de *bloqueos aplicados* y lista de *eventos recientes* (estado de cada capa).
  - **Listas** — dominios/palabras cargados, fuentes y *Actualizar listas*.
  - **Ajustes** — apariencia (tema), seguridad (contraseña/admin/guardián) y permisos de notificación.
  - **Más** — acerca de, donación, GitHub y marca de agua.
- **Tarjeta de estado** con escudo (rojo = inactivo / verde = activo) y métricas en
  vivo: dominios, palabras, bloqueos, contraseña, admin, guardián, bloqueo de
  búsquedas y Device Owner.
- **Tema claro/oscuro** con transición animada (por defecto oscuro). Se cambia en
  *Ajustes → Apariencia* o en el menú **⋮** de la cabecera; se recuerda entre sesiones.
- **Centro de notificaciones** (campana 🔔) con avisos derivados del **estado real**
  (protección inactiva, sin contraseña, admin/guardián inactivos, bloqueos aplicados).
  El punto azul aparece cuando hay algo que requiere tu atención.
- **Donación** con botón **PayPal** minimalista (en *Más*).
- **GitHub** del proyecto y **marca de agua** *"Brayan Cortés · Desarrollador Full Stack"*.

> **Compatibilidad:** diseñada para **Android 14+** (probada contra API 34–36).
> El `minSdk` se mantiene en 26 para no dejar fuera equipos más antiguos; el diseño
> se ve idéntico en todas las versiones.

---

## ¿Cómo funciona? (igual que en el PC)

En el PC, `filtro.py` escucha en `127.0.0.1:53` y decide por cada consulta DNS.
En Android se usa **`VpnService`**: una VPN **local, en el propio teléfono, sin
root**, que captura el DNS y aplica exactamente la misma lógica:

1. Parsea cada consulta DNS.
2. Si el dominio está **en una lista** (exacto o dominio padre) o **contiene una
   palabra clave** → responde `0.0.0.0` (A) / `::` (AAAA) / `NXDOMAIN`.
3. Si se permite → la reenvía al upstream real (`1.1.1.1` / `1.0.0.1`) por un
   socket **protegido** (no entra en bucle por la VPN).

Intercepta también los **DNS públicos hardcodeados** (8.8.8.8, 9.9.9.9, etc.)
para que las apps no se salten el filtro usando su propio servidor.

### Listas (las mismas del repo de PC)
- **Empaquetadas** (en `assets/`, funcionan sin internet): `dominios_personalizados.txt`,
  `doh_bypass.txt`, `palabras_clave.txt`.
- **Descargables** (botón *Actualizar listas*, ~260k dominios): StevenBlack porn
  + Hagezi NSFW. Se guardan en el almacenamiento interno de la app.

El parser entiende formato *hosts* (`0.0.0.0 dominio`), dominio plano y sintaxis
Adblock (`||dominio^`), por lo que la lista de Hagezi se aplica correctamente
(mejora respecto al PC).

---

## 📖 Conceptos clave (definiciones)

### Filtro DNS / VpnService
Una **VPN local** que NO manda tu tráfico a ningún servidor externo: todo se
queda en el teléfono. Solo sirve para **leer las consultas DNS** (los nombres de
las webs que se piden) y decidir si se permiten o se bloquean. No descifra nada
ni espía contenido; solo trabaja con **nombres de dominio**.

### Device Admin (Administrador de dispositivo)
Un nivel de permiso estándar de Android (pensado originalmente para que empresas
apliquen políticas). Lo que nos importa: **mientras el Filtro sea "administrador
de dispositivo" activo, Android te obliga a desactivarlo ANTES de poder
desinstalar la app**. Por sí solo no impide borrar la app: añade un paso
obligatorio. Se activa con un toque (sin PC) y se desactiva desde Ajustes
(y ahí es donde el Guardián vigila).

### Guardián de accesibilidad (Accessibility Service)
El sistema de **Accesibilidad** de Android permite a una app "ver" lo que hay en
pantalla y hacer acciones (creado para personas con discapacidad). Aquí se usa
como **vigilante**: detecta cuando entras a las pantallas de *Desinstalar /
Información de la app / Forzar detención / Desactivar administrador / Accesibilidad*
de **esta** app y te saca de ahí (atrás + inicio), salvo que hayas abierto la
**ventana de desbloqueo con contraseña**. **Es la capa que de verdad frena el
impulso.** Se activa a mano en Ajustes → Accesibilidad (no se puede automatizar)
y **NO funciona en Modo Seguro**.

### Bloqueo de búsquedas por palabra (NUEVO)
El **mismo** servicio de accesibilidad ahora hace un segundo trabajo: en los
navegadores **lee el texto** de la barra de búsqueda / URL y, si contiene un
término explícito del catálogo, **tapa la pantalla** con un aviso y te regresa.
Es el equivalente móvil de la "capa de búsquedas" del PC.

- **Sin SafeSearch y sin descifrar tráfico:** lee el **texto en pantalla** (lo
  que se rinde por accesibilidad), no la red. Funciona aunque sea HTTPS.
- **Mismo catálogo que el PC:** `catalogo_busqueda.txt` + `actrices_porno.txt`,
  con `excepciones_educativas.txt` (pene, vagina, menstruación… **no** bloquean
  solas: para biología/lectura). Solo bloquea si hay un término explícito.
- **No estorba en uso normal:** solo actúa al detectar una búsqueda explícita;
  el resto del tiempo solo lee en segundo plano (no dibuja ni ralentiza nada).
- **Cobertura:** navegadores que exponen el texto por accesibilidad (Chrome,
  Samsung Internet, Firefox, Brave, Edge, app de Google, etc.).
- Usa el **mismo** Guardián de accesibilidad (no pide permiso extra).

### Device Owner (Propietario del dispositivo)
El modo de gestión **más fuerte** de Android: el teléfono queda "en propiedad"
de la app a nivel de sistema. Se activa **una sola vez por ADB desde un PC** y
solo si el teléfono **no tiene cuentas añadidas**. Permite `setUninstallBlocked`
(la opción de *Desinstalar* **ni aparece**), apagar el DNS privado y forzar la
VPN always-on. Para quitarlo se necesita **ADB** (`dpm remove-active-admin`) o
**reset de fábrica**: el Modo Seguro NO lo quita.
> ⚠️ **No recomendado para uso sin PC:** si lo activas y borras la contraseña,
> tu única salida sin computadora sería resetear de fábrica (borra todo). Déjalo
> **desactivado** salvo que siempre tengas tu PC.

### ADB (Android Debug Bridge)
Una herramienta que corre en tu **PC** (viene con el SDK de Android) y manda
comandos al teléfono por cable USB. **Usarlo es simple:**
1. Activa **Opciones de desarrollador** y **Depuración USB** en el teléfono.
2. Conecta el cable y acepta el aviso *"¿Permitir depuración USB?"*.
3. Ejecuta el comando o el `.bat`. **Eso es todo.**

### Contraseña (candado)
Solo se guarda el **hash SHA-256**, nunca el texto plano (igual que el PC).
Recomendación de autocontrol: que la escriba una persona de confianza y **no te
la diga**, o destrúyela (ver opciones de recuperación).

---

## 🛡️ Resistencia a la desinstalación (3 capas)

> **Honestidad de ingeniería:** en Android **ninguna** app normal es 100 %
> imposible de borrar. Siempre quedan el **Modo Seguro**, el **restablecimiento
> de fábrica** y **ADB**. Lo que se consigue es **fricción muy fuerte**, que es
> justo lo que frena el impulso.

| Capa | Qué hace | Se rompe con |
|------|----------|--------------|
| **1. Administrador de dispositivo** | Obliga a *desactivar* el admin antes de desinstalar. | Desactivar el admin (la capa 2 lo vigila). |
| **2. Guardián de accesibilidad** | Te saca de las pantallas de desinstalar/forzar/desactivar salvo desbloqueo por contraseña. | Modo Seguro, reset de fábrica, ADB. |
| **3. Device Owner** (opcional) | `setUninstallBlocked`: *Desinstalar* ni aparece. Apaga DNS privado y fuerza VPN always-on. | ADB (`dpm remove-active-admin`) o reset de fábrica. |

---

## 🔓 Cómo quitar la protección (cada opción)

Detalle paso a paso en **[`RECUPERACION.md`](RECUPERACION.md)**. Resumen:

| Vía | ¿PC? | ¿Contraseña? | ¿Funciona con Device Owner? |
|-----|:----:|:------------:|:---------------------------:|
| **Desactivar en la app** | No | **Sí** | Sí |
| **Modo Seguro** | No | No | **No** |
| **ADB** (`device-owner/quitar_device_owner.bat`) | **Sí** | No | Sí |
| **Reset de fábrica** | No | No | Sí (borra todo) |

1. **Con contraseña:** abre la app → *Desactivar protección* → escríbela. Detiene
   la VPN, quita el admin y te da 5 min para desinstalar.
2. **Modo Seguro (sin PC, sin contraseña):** mantén pulsado *Encendido* →
   mantén pulsado *"Apagar"* → *"Reiniciar en modo seguro"*. Ahí el Guardián no
   corre → Ajustes → *Apps de administración del dispositivo* → desactiva
   "Filtro de Contenido" → Ajustes → *Aplicaciones* → Desinstalar.
3. **ADB (con PC):** conecta por depuración USB y ejecuta `quitar_device_owner.bat`.
4. **Reset de fábrica:** Ajustes → Sistema → Restablecer → Borrar todos los datos
   (último recurso, borra el teléfono).

### ⭐ Configuración recomendada (la elegida)
**Borrar la contraseña + solo capas 1 y 2 (SIN Device Owner).** Así:
- El impulso choca con la contraseña destruida y el Guardián.
- Tu salida sin PC y sin contraseña es el **Modo Seguro** (opción 2).
- Nunca quedas atrapado ni dependes de una computadora.

---

## Compilar e instalar desde el código

### Opción A — Android Studio (recomendada)
1. *File → Open* y abre la carpeta del proyecto.
2. Espera la sincronización de Gradle (descarga AGP/Kotlin la primera vez).
3. Conecta el teléfono con **depuración USB** y pulsa **Run** (▶).

### Opción B — Línea de comandos
```bash
# JAVA_HOME debe apuntar a un JDK 17+ (el de Android Studio sirve)
gradlew.bat assembleDebug
# APK resultante: app/build/outputs/apk/debug/app-debug.apk
```

### Firma de release (para distribuir y poder actualizar)
El APK que se publica en **`releases/`** va firmado con una **clave de release
propia y estable** (no la clave de depuración). Esto es lo que permite
**actualizar** la app más adelante sin que Android rechace la nueva versión por
"firma distinta" — algo crítico cuando el anti-desinstalación está activo.

- Los secretos viven en **`keystore.properties`** y en **`release.keystore`**, ambos
  **fuera de git** (`.gitignore`). **Guárdalos y respáldalos**: si pierdes el
  keystore o su contraseña, **nunca** podrás publicar una actualización.
- Compilar el APK firmado:
  ```bash
  gradlew.bat assembleRelease
  # APK resultante: app/build/outputs/apk/release/app-release.apk
  ```
- En otra máquina sin `keystore.properties`, el build no se rompe: el release
  sale sin firmar (hay que aportar el keystore para firmarlo).
- Para cada versión nueva, sube `versionCode` en `app/build.gradle.kts`.

### Primer uso
1. Abre la app → **Establecer contraseña** (o decide destruirla / dársela a alguien).
2. **Activar protección** → acepta el permiso de VPN.
3. **Activar administrador de dispositivo** y **Activar guardián de accesibilidad**.
4. (Opcional) **Actualizar listas** para descargar los ~260k dominios.
5. *(No recomendado sin PC)* Device Owner por ADB: ver [`device-owner/`](device-owner/).

---

## Límites técnicos (honestos, los mismos del PC)
- El **filtro DNS** bloquea **destinos** (dominios). El **texto** que escribes en
  el buscador lo bloquea la **capa de búsquedas** (accesibilidad, ver arriba),
  que solo ve lo que se rinde como texto en pantalla (no miniaturas de imagen).
- **DNS privado / DoT** (Ajustes → Red → DNS privado) puede saltarse la VPN. Solo
  se fuerza a OFF con Device Owner; sin él, ponlo en "Desactivado" a mano.
- Apps con **DoH** propio: se bloquean sus dominios conocidos (`doh_bypass.txt`),
  no el tráfico cifrado en sí.

---

## ¿Y iOS?
Resumen en [`docs/iOS.md`](docs/iOS.md). En corto: en iOS **sí** se puede un
filtro DNS (Network Extension), pero **no** se puede impedir la desinstalación
salvo que el dispositivo esté **supervisado (MDM)** con Apple Configurator
(requiere un Mac y una cuenta de Apple Developer).

---

## Estructura del proyecto
```
app/src/main/
├── java/com/brayan/filtrocontenido/
│   ├── App.kt                          Application: aplica el tema guardado (solo UI)
│   ├── MainActivity.kt                 UI, navegación por pestañas y flujos
│   ├── data/UiPrefs.kt                 preferencia de tema claro/oscuro (solo UI)
│   ├── vpn/DnsVpnService.kt            VPN local que filtra el DNS (= filtro.py)
│   ├── net/IpUdp.kt · Dns.kt           parseo/armado de paquetes IPv4/UDP y DNS
│   ├── filter/DomainFilter.kt          lógica dominio + palabra clave (= PC)
│   ├── filter/SearchTermFilter.kt      motor de bloqueo de BÚSQUEDAS (= motor_busqueda.py)
│   ├── filter/BlocklistManager.kt      carga assets + descarga StevenBlack/Hagezi
│   ├── BloqueoBusquedaActivity.kt      pantalla de bloqueo de búsqueda
│   ├── admin/FilterDeviceAdminReceiver.kt · DeviceOwnerManager.kt
│   ├── guard/UninstallGuardService.kt  guardián accesibilidad + bloqueo búsquedas
│   ├── boot/BootReceiver.kt            arranque con el teléfono
│   ├── lock/PasswordManager.kt         SHA-256 (sin texto plano)
│   └── data/Prefs.kt
│   └── res/ · assets/
device-owner/   scripts ADB (capa 3) + instrucciones
docs/iOS.md     evaluación de iOS
releases/       APK listo para instalar
```

---

Licencia MIT · Brayan Cortés Leytón · 2026

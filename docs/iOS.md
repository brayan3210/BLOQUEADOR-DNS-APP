# Evaluación iOS (para el futuro)

Pregunta: ¿se puede hacer lo mismo en iPhone, exactamente igual y sin poder
desinstalarse? Respuesta honesta, como dev senior:

## Lo que SÍ se puede
- **Filtro DNS on-device**, equivalente a la VPN local de Android, con una
  **Network Extension**:
  - `NEDNSProxyProvider` (proxy de DNS) o `NEPacketTunnelProvider` (túnel tipo VPN),
  - o `NEFilterDataProvider` (filtro de contenido de red).
- Reutilizar las **mismas listas** (StevenBlack + Hagezi + dominios/keywords).
- Bloqueo por dominio y por palabra clave: misma lógica que el PC y Android.
- Persistencia: las Network Extensions de iOS arrancan solas y son difíciles de
  matar; con un **perfil de configuración** se puede activar "always-on".

## Lo que NO se puede (límite duro de Apple)
- **Impedir la desinstalación NO es posible** en un iPhone normal. iOS no tiene
  equivalente al *Device Admin* / *Device Owner* de Android para apps de
  terceros. El usuario siempre puede borrar la app.
- La **única** forma de bloquear la desinstalación es que el dispositivo esté
  **supervisado (MDM)**:
  - Supervisar con **Apple Configurator** (requiere un **Mac** y, normalmente,
    borrar el iPhone para supervisarlo), o un servidor **MDM**.
  - Con supervisión se puede aplicar la restricción *"no permitir eliminar apps"*
    y bloquear cambios de DNS.
- **Screen Time** (Tiempo en pantalla) con código de un tercero da fricción extra
  (restricciones de contenido), pero no bloquea borrar una app instalada por ti.

## Requisitos prácticos si algún día lo haces
- Un **Mac** (Xcode obligatorio; no se compila iOS en Windows).
- Cuenta **Apple Developer** ($99/año) para instalar en tu propio iPhone sin que
  caduque cada 7 días.
- Para "no desinstalable": **supervisar** el iPhone con Apple Configurator + un
  **perfil de configuración** con la restricción de apps y DNS bloqueado.

## Recomendación
La versión **Android es estrictamente más capaz** para tu objetivo
(anti-desinstalación real con Device Owner). En iOS lo máximo equivalente es:
**filtro DNS por Network Extension + iPhone supervisado vía Apple Configurator**.
Se diseña cuando tengas Mac + iPhone; la lógica de filtrado y las listas se
reaprovechan tal cual.

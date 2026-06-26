# Device Owner (capa 3) — máxima dureza

Convierte la app en **Device Owner**. Con eso:
- la opción de **desinstalar ni aparece** (`setUninstallBlocked`),
- se **desactiva el DNS privado (DoT)** del sistema (que evadiría la VPN),
- se fuerza la **VPN always-on con lockdown** (sin VPN, no hay red).

## Requisitos (una sola vez, desde tu PC)
1. **Depuración USB** activada en el teléfono (Ajustes → Opciones de desarrollador).
2. El teléfono **no debe tener cuentas añadidas** (Google, Samsung, etc.). Si las
   tiene, quítalas en *Ajustes → Cuentas*, o hazlo recién restablecido de fábrica.
3. La app **ya instalada** en el teléfono.
4. `adb` disponible (viene con el SDK de Android / Android Studio:
   `C:\Users\braya\AppData\Local\Android\Sdk\platform-tools\adb.exe`).

## Activar
Ejecuta el script:
```
device-owner\activar_device_owner.bat
```
o el comando manual:
```
adb shell dpm set-device-owner com.brayan.filtrocontenido/.admin.FilterDeviceAdminReceiver
```
Debe responder: `Success: Device owner set to package ...`.
Abre la app una vez para que aplique el blindaje.

## Quitar (recuperación)
Si algún día quieres revertirlo (decisión en frío):
```
device-owner\quitar_device_owner.bat
```
o manualmente:
```
adb shell dpm remove-active-admin --user 0 com.brayan.filtrocontenido/.admin.FilterDeviceAdminReceiver
adb shell pm uninstall --user 0 com.brayan.filtrocontenido
```

> Aun siendo Device Owner, el **restablecimiento de fábrica** borra todo y es la
> única vía garantizada de quitarlo sin PC. Eso es deliberado: fricción máxima,
> no una cárcel.

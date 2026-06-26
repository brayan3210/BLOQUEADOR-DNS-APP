@echo off
REM Activa el Filtro de Contenido como Device Owner (capa 3, maxima dureza).
REM Requiere: depuracion USB ON, telefono SIN cuentas anadidas, app ya instalada.
setlocal
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb

echo === Dispositivos conectados ===
"%ADB%" devices
echo.
echo Activando Device Owner...
"%ADB%" shell dpm set-device-owner com.brayan.filtrocontenido/.admin.FilterDeviceAdminReceiver
echo.
echo Si dice "Success", abre la app una vez para aplicar el blindaje.
pause

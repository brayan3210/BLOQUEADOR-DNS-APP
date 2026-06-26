@echo off
REM ============================================================
REM  RECUPERACION: quita el Device Owner / administrador del
REM  Filtro de Contenido y desinstala la app.
REM
REM  Lo necesitas SI activaste Device Owner (capa 3) y borraste
REM  la contrasena. Con Device Owner, el Modo Seguro NO sirve:
REM  esta es la unica salida sin resetear de fabrica.
REM
REM  Requisitos (desde tu PC):
REM    1) Depuracion USB activada en el telefono.
REM    2) Cable USB conectado y aceptar "Permitir depuracion USB".
REM ============================================================
setlocal
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb
set COMP=com.brayan.filtrocontenido/.admin.FilterDeviceAdminReceiver
set PKG=com.brayan.filtrocontenido

echo === Dispositivos conectados (debe aparecer tu telefono) ===
"%ADB%" devices
echo.
echo [1/3] Quitando Device Owner / administrador...
"%ADB%" shell dpm remove-active-admin --user 0 %COMP%
"%ADB%" shell dpm remove-active-admin %COMP%
echo.
echo [2/3] Desinstalando la app...
"%ADB%" shell pm uninstall --user 0 %PKG%
"%ADB%" uninstall %PKG%
echo.
echo [3/3] Hecho.
echo Si algo fallo (telefono no detectado, comando rechazado),
echo el ultimo recurso GARANTIZADO es resetear de fabrica.
pause

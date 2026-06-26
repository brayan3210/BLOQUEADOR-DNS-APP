@echo off
REM Quita el Device Owner (recuperacion). Decision en frio, desde tu PC.
setlocal
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb

"%ADB%" devices
echo Quitando Device Owner / administrador...
"%ADB%" shell dpm remove-active-admin com.brayan.filtrocontenido/.admin.FilterDeviceAdminReceiver
echo Hecho. Ahora puedes desinstalar la app normalmente.
pause

@echo off
rem shortcut to boot JBossAS with CapeDwarf configuration
rem if you need to set other boot options, please use standalone.bat

if "%OS%" == "Windows_NT" (
  set "DIRNAME=%~dp0%"
) else (
  set DIRNAME=.\
)

rem check if we need to run bytecode transformation
if not exist %DIRNAME%..\modules\com\google\appengine\main\appengine-api-1.0-sdk*-capedwarf* (
  call %DIRNAME%capedwarf-bytecode.bat
)

if "%1" == "" (
  %DIRNAME%standalone.bat -c standalone-capedwarf.xml
) else (
  cmd /c "cd %1 && %DIRNAME%standalone.bat -c standalone-capedwarf.xml -DrootDeployment=%1"
)

@echo off
rem shortcut to boot JBossAS with CapeDwarf configuration
rem if you need to set other boot options, please use standalone.bat

if "%OS%" == "Windows_NT" (
  set "DIRNAME=%~dp0%"
) else (
  set DIRNAME=.\
)

rem check if we need to run bytecode transformation
if not exist %DIRNAME%..\modules\com\google\appengine\main\old-appengine-api-1.0-sdk* (
	%DIRNAME%capedwarf-bytecode.bat
)

%DIRNAME%standalone.bat -c standalone-capedwarf.xml

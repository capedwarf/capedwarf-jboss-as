@echo off
rem shortcut to boot JBossAS with CapeDwarf configuration
rem if you need to set other boot options, please use standalone.bat

if "%OS%" == "Windows_NT" (
  set "DIRNAME=%~dp0%"
) else (
  set DIRNAME=.\
)

%DIRNAME%standalone.bat -c standalone-capedwarf.xml

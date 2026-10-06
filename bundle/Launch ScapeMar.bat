@echo off
cd /d "%~dp0"
runtime\bin\java.exe -cp . ScapeMarLauncher
if errorlevel 1 pause

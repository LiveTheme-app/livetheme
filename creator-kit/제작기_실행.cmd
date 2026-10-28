@echo off
powershell.exe -NoProfile -File "%~dp0start-studio.ps1"
if errorlevel 1 pause

@echo off
REM Runs reset.ps1 in this folder with Windows PowerShell. Double-click this file.
REM -ExecutionPolicy Bypass applies only to this one run, so the script runs without changing system settings.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0reset.ps1"
pause

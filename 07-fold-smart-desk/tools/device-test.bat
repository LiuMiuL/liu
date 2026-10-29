@echo off
rem 더블클릭용: 같은 폴더의 device-test.ps1을 실행합니다.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0device-test.ps1" %*
pause

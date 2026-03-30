@echo off
chcp 65001 >nul
echo Converting start-backend.bat to UTF-8 with BOM...
powershell -Command "$bom = New-Object System.Text.UTF8Encoding $true; $content = Get-Content 'start-backend.bat' -Raw; [System.IO.File]::WriteAllText('start-backend-fixed.bat', $content, $bom)"
echo Done!
echo Fixed file: start-backend-fixed.bat
pause

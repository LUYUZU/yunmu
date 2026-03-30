$bom = New-Object System.Text.UTF8Encoding $true
$content = Get-Content 'start-backend.bat' -Raw
[System.IO.File]::WriteAllText('start-backend-fixed.bat', $content, $bom)
Write-Host "Conversion completed!"
Write-Host "File: start-backend-fixed.bat"
Write-Host "Encoding: UTF-8 with BOM"

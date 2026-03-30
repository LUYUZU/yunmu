# Convert file to UTF-8 with BOM and CRLF line endings
$filePath = "D:\基于机器学习的多模态高原牛羊行为监测系统\start-backend-fixed.bat"
Write-Host "Converting file: $filePath"
$content = Get-Content -Path $filePath -Raw -Encoding UTF8
# Ensure CRLF line endings
$content = $content -replace "`r?`n", "`r`n"
# Save as UTF-8 with BOM
$utf8BOM = New-Object System.Text.UTF8Encoding $true
[System.IO.File]::WriteAllText($filePath, $content, $utf8BOM)
Write-Host "Conversion completed!"
Write-Host "File: $filePath"
Write-Host "Encoding: UTF-8 with BOM"
Write-Host "Line endings: CRLF"

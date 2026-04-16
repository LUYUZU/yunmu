$connStr = "Server=localhost,1433;Database=yunmu_db;User Id=sa;Password=1234567890;TrustServerCertificate=True;"
$conn = New-Object System.Data.SqlClient.SqlConnection($connStr)
$conn.Open()
$cmd = $conn.CreateCommand()

# List all tables
$cmd.CommandText = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME"
$reader = $cmd.ExecuteReader()
Write-Output "=== Tables in yunmu_db ==="
while ($reader.Read()) { Write-Output "  $($reader['TABLE_NAME'])" }
$reader.Close()

# Check animals table structure
$cmd.CommandText = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'animals' ORDER BY ORDINAL_POSITION"
$reader = $cmd.ExecuteReader()
Write-Output "`n=== animals table ==="
while ($reader.Read()) { Write-Output "  $($reader['COLUMN_NAME']) | $($reader['DATA_TYPE'])" }
$reader.Close()

# Check devices table
$cmd.CommandText = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'devices' ORDER BY ORDINAL_POSITION"
$reader = $cmd.ExecuteReader()
Write-Output "`n=== devices table ==="
while ($reader.Read()) { Write-Output "  $($reader['COLUMN_NAME']) | $($reader['DATA_TYPE'])" }
$reader.Close()

# Check posture_records table
$cmd.CommandText = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'posture_records' ORDER BY ORDINAL_POSITION"
$reader = $cmd.ExecuteReader()
Write-Output "`n=== posture_records table ==="
while ($reader.Read()) { Write-Output "  $($reader['COLUMN_NAME']) | $($reader['DATA_TYPE'])" }
$reader.Close()

# Check current animal ids
$cmd.CommandText = "SELECT id, name, device_id FROM animals"
$reader = $cmd.ExecuteReader()
Write-Output "`n=== Current animals ==="
while ($reader.Read()) { Write-Output "  id=$($reader['id']) name=$($reader['name']) device=$($reader['device_id'])" }
$reader.Close()

$conn.Close()

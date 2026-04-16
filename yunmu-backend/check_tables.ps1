$connStr = "Server=localhost,1433;Database=yunmu_db;User Id=sa;Password=1234567890;TrustServerCertificate=True;"
$conn = New-Object System.Data.SqlClient.SqlConnection($connStr)
$conn.Open()
$cmd = $conn.CreateCommand()

$tables = @("gps_locations", "posture_results", "behavior_results", "location_tracks", "health_status", "daily_step_summary", "sensor_data")

foreach ($t in $tables) {
    $cmd.CommandText = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '$t' ORDER BY ORDINAL_POSITION"
    $reader = $cmd.ExecuteReader()
    Write-Output "`n=== $t ==="
    while ($reader.Read()) { Write-Output "  $($reader['COLUMN_NAME']) | $($reader['DATA_TYPE'])" }
    $reader.Close()
}

$conn.Close()

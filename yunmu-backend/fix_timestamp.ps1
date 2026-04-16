# Fix timestamp column - bigint to datetime2
$connStr = "Server=localhost,1433;Database=yunmu_db;User Id=sa;Password=1234567890;TrustServerCertificate=True;"
$conn = New-Object System.Data.SqlClient.SqlConnection($connStr)
$conn.Open()
$cmd = $conn.CreateCommand()

# Check current timestamp type
$cmd.CommandText = "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'step_counts' AND COLUMN_NAME = 'timestamp'"
$type = $cmd.ExecuteScalar()
Write-Output "Current timestamp type: $type"

if ($type -eq "bigint") {
    Write-Output "Converting timestamp from bigint to datetime2..."
    
    # Backup data first
    $cmd.CommandText = "ALTER TABLE step_counts ADD ts_backup BIGINT"
    $cmd.ExecuteNonQuery() | Out-Null
    $cmd.CommandText = "UPDATE step_counts SET ts_backup = timestamp"
    $cmd.ExecuteNonQuery() | Out-Null
    Write-Output "Backup done, dropping old column..."
    
    # Drop old and create new
    $cmd.CommandText = "ALTER TABLE step_counts DROP COLUMN timestamp"
    $cmd.ExecuteNonQuery() | Out-Null
    Write-Output "Old column dropped, adding new datetime2 column..."
    
    $cmd.CommandText = "ALTER TABLE step_counts ADD timestamp DATETIME2"
    $cmd.ExecuteNonQuery() | Out-Null
    Write-Output "New datetime2 column added"
    
    # Convert data
    $cmd.CommandText = "UPDATE step_counts SET timestamp = DATEADD(SECOND, ts_backup/1000, '19700101') WHERE ts_backup IS NOT NULL"
    $affected = $cmd.ExecuteNonQuery()
    Write-Output "Converted $affected rows"
    
    # Drop backup
    $cmd.CommandText = "ALTER TABLE step_counts DROP COLUMN ts_backup"
    $cmd.ExecuteNonQuery() | Out-Null
    Write-Output "Backup column dropped"
}

# Verify
$cmd.CommandText = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'step_counts' AND COLUMN_NAME IN ('timestamp', 'steps', 'step_count') ORDER BY ORDINAL_POSITION"
$reader = $cmd.ExecuteReader()
Write-Output "`n=== Verification ==="
while ($reader.Read()) {
    Write-Output ("  " + $reader["COLUMN_NAME"] + " | " + $reader["DATA_TYPE"])
}
$reader.Close()
$conn.Close()
Write-Output "`nDone."

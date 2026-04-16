# Fix timestamp column - bigint to datetime2 (handling dependencies)
$connStr = "Server=localhost,1433;Database=yunmu_db;User Id=sa;Password=1234567890;TrustServerCertificate=True;"
$conn = New-Object System.Data.SqlClient.SqlConnection($connStr)
$conn.Open()
$cmd = $conn.CreateCommand()

# Step 1: Check existing indexes on step_counts
$cmd.CommandText = "SELECT name FROM sys.indexes WHERE object_id = OBJECT_ID('step_counts')"
$reader = $cmd.ExecuteReader()
Write-Output "=== Existing indexes ==="
while ($reader.Read()) { Write-Output "  $($reader['name'])" }
$reader.Close()

# Step 2: Check constraints
$cmd.CommandText = "SELECT name FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID('step_counts')"
$reader = $cmd.ExecuteReader()
Write-Output "`n=== Key constraints ==="
while ($reader.Read()) { Write-Output "  $($reader['name'])" }
$reader.Close()

# Step 3: Drop index that depends on timestamp
$cmd.CommandText = "IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_device_time' AND object_id = OBJECT_ID('step_counts'))
BEGIN DROP INDEX idx_device_time ON step_counts; PRINT 'Dropped index idx_device_time'; END"
$cmd.ExecuteNonQuery() | Out-Null

# Step 4: Backup timestamp data
$cmd.CommandText = "ALTER TABLE step_counts ADD ts_backup BIGINT"
$cmd.ExecuteNonQuery() | Out-Null
$cmd.CommandText = "UPDATE step_counts SET ts_backup = timestamp"
$cmd.ExecuteNonQuery() | Out-Null
Write-Output "`nBackup: ts_backup created and populated"

# Step 5: Drop old timestamp
$cmd.CommandText = "ALTER TABLE step_counts DROP COLUMN timestamp"
$cmd.ExecuteNonQuery() | Out-Null
Write-Output "Old timestamp column dropped"

# Step 6: Add new datetime2 timestamp
$cmd.CommandText = "ALTER TABLE step_counts ADD timestamp DATETIME2"
$cmd.ExecuteNonQuery() | Out-Null
Write-Output "New datetime2 timestamp column added"

# Step 7: Convert data (bigint ms -> datetime2)
$cmd.CommandText = "UPDATE step_counts SET timestamp = DATEADD(SECOND, ts_backup/1000, '19700101') WHERE ts_backup IS NOT NULL"
$affected = $cmd.ExecuteNonQuery()
Write-Output "Converted $affected rows from epoch ms to datetime2"

# Step 8: Drop backup
$cmd.CommandText = "ALTER TABLE step_counts DROP COLUMN ts_backup"
$cmd.ExecuteNonQuery() | Out-Null
Write-Output "Backup column removed"

# Step 9: Recreate index
$cmd.CommandText = "CREATE INDEX idx_device_time ON step_counts(device_id, timestamp)"
$cmd.ExecuteNonQuery() | Out-Null
Write-Output "Index idx_device_time recreated"

# Step 10: Final verification
$cmd.CommandText = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'step_counts' ORDER BY ORDINAL_POSITION"
$reader = $cmd.ExecuteReader()
Write-Output "`n=== Final step_counts structure ==="
while ($reader.Read()) {
    Write-Output ("  " + $reader["COLUMN_NAME"] + " | " + $reader["DATA_TYPE"])
}
$reader.Close()
$conn.Close()
Write-Output "`nDone."

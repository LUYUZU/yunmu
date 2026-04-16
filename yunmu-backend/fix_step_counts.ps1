# Fix step_counts table structure
$connStr = "Server=localhost,1433;Database=yunmu_db;User Id=sa;Password=1234567890;TrustServerCertificate=True;"
$conn = New-Object System.Data.SqlClient.SqlConnection($connStr)
$conn.Open()
$cmd = $conn.CreateCommand()

# Step 1: Drop step_count column if exists
$cmd.CommandText = "IF EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'step_counts' AND COLUMN_NAME = 'step_count')
BEGIN ALTER TABLE step_counts DROP COLUMN step_count; PRINT 'Dropped step_count'; END"
try { $cmd.ExecuteNonQuery() | Out-Null } catch { Write-Output "step_count drop: $($_.Exception.Message)" }

# Step 2: Add steps column if not exists
$cmd.CommandText = "IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'step_counts' AND COLUMN_NAME = 'steps')
BEGIN ALTER TABLE step_counts ADD steps INT DEFAULT 0; PRINT 'Added steps'; END"
try { $cmd.ExecuteNonQuery() | Out-Null } catch { Write-Output "steps add: $($_.Exception.Message)" }

# Step 3: Convert timestamp from bigint to datetime2
$cmd.CommandText = "IF EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'step_counts' AND COLUMN_NAME = 'timestamp' AND DATA_TYPE = 'bigint')
BEGIN
    ALTER TABLE step_counts ADD ts_backup BIGINT;
    UPDATE step_counts SET ts_backup = timestamp;
    ALTER TABLE step_counts DROP COLUMN timestamp;
    ALTER TABLE step_counts ADD timestamp DATETIME2;
    UPDATE step_counts SET timestamp = DATEADD(SECOND, ts_backup/1000, '19700101') WHERE ts_backup IS NOT NULL;
    ALTER TABLE step_counts DROP COLUMN ts_backup;
    PRINT 'Converted timestamp bigint -> datetime2';
END"
try { $cmd.ExecuteNonQuery() | Out-Null } catch { Write-Output "timestamp convert: $($_.Exception.Message)" }

# Step 4: Verify final structure
$cmd.CommandText = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'step_counts' ORDER BY ORDINAL_POSITION"
$reader = $cmd.ExecuteReader()
Write-Output "=== Current step_counts structure ==="
while ($reader.Read()) {
    Write-Output ("  " + $reader["COLUMN_NAME"] + " | " + $reader["DATA_TYPE"])
}
$reader.Close()
$conn.Close()
Write-Output "Done."

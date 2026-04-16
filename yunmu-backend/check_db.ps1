Add-Type -AssemblyName "System.Data"
$connStr = "Server=localhost,1433;Database=yunmu_db;User Id=sa;Password=1234567890;TrustServerCertificate=True;"
$conn = New-Object System.Data.SqlClient.SqlConnection($connStr)
$conn.Open()
$cmd = $conn.CreateCommand()
$cmd.CommandText = "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'step_counts' ORDER BY ORDINAL_POSITION"
$reader = $cmd.ExecuteReader()
while ($reader.Read()) {
    Write-Output ($reader["COLUMN_NAME"] + " | " + $reader["DATA_TYPE"] + " | " + $reader["IS_NULLABLE"])
}
$reader.Close()
$conn.Close()
Write-Output "---Done---"

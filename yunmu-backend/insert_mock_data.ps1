# ============================================================================
# 模拟数据生成脚本 - 高原牛羊监测系统
# 动物: 小花(7249), 大黄(7250), 小白(7251)
# ============================================================================

$connStr = "Server=localhost,1433;Database=yunmu_db;User Id=sa;Password=1234567890;TrustServerCertificate=True;"
$conn = New-Object System.Data.SqlClient.SqlConnection($connStr)
$conn.Open()

function Invoke-Sql($sql, $desc) {
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = $sql
    try {
        $n = $cmd.ExecuteNonQuery()
        Write-Output "  [OK] $desc"
    } catch {
        Write-Output "  [ERR] $desc : $($_.Exception.Message.Split("`n")[0])"
    }
}

# Base coordinates (Tibetan Plateau)
$baseCoords = @{
    "7249" = @{ lat = 29.6500; lng = 91.1000 }
    "7250" = @{ lat = 29.6515; lng = 91.0990 }
    "7251" = @{ lat = 29.6490; lng = 91.1010 }
}

$animals = @("7249", "7250", "7251")
$now = Get-Date
$today = $now.ToString("yyyy-MM-dd")
$todayStart = [DateTime]::Parse($today)
$nowUtcTicks = $now.ToUniversalTime().Ticks / 10000
$startUtcTicks = $todayStart.ToUniversalTime().Ticks / 10000
$rng = New-Object System.Random(42)

Write-Output "=== Clearing old test data ==="
Invoke-Sql "DELETE FROM gps_locations WHERE animal_id IN ('7249','7250','7251')" "gps_locations cleared"
Invoke-Sql "DELETE FROM posture_results WHERE animal_id IN ('7249','7250','7251')" "posture_results cleared"
Invoke-Sql "DELETE FROM behavior_results WHERE animal_id IN ('7249','7250','7251')" "behavior_results cleared"
Invoke-Sql "DELETE FROM location_tracks WHERE animal_id IN ('7249','7250','7251')" "location_tracks cleared"
Invoke-Sql "DELETE FROM health_status WHERE animal_id IN ('7249','7250','7251')" "health_status cleared"
Invoke-Sql "DELETE FROM step_counts WHERE animal_id IN ('7249','7250','7251')" "step_counts cleared"
Invoke-Sql "DELETE FROM daily_step_summary WHERE animal_id IN ('7249','7250','7251')" "daily_step_summary cleared"
Invoke-Sql "DELETE FROM sensor_data WHERE animal_id IN ('7249','7250','7251')" "sensor_data cleared"

Write-Output ""
Write-Output "=== GPS Locations (200 records x 3 animals) ==="
foreach ($deviceId in $animals) {
    $base = $baseCoords[$deviceId]
    for ($i = 0; $i -lt 200; $i++) {
        $ts = [long]($startUtcTicks + $i * 5 * 60 * 1000)
        $lat = [Math]::Round($base.lat + ($rng.NextDouble() - 0.5) * 0.002, 6)
        $lng = [Math]::Round($base.lng + ($rng.NextDouble() - 0.5) * 0.002, 6)
        $alt = [Math]::Round(3650 + ($rng.NextDouble() - 0.5) * 50, 1)
        $speed = [Math]::Round($rng.NextDouble() * 3.0, 2)
        $sat = $rng.Next(5, 12)
        $fix = if ($sat -ge 6) { 1 } else { 0 }
        $sql = "INSERT INTO gps_locations (device_id,animal_id,longitude,latitude,altitude,speed,satellites,fix_quality,source,timestamp,created_at) VALUES ('$deviceId','$deviceId',$lng,$lat,$alt,$speed,$sat,$fix,'GPS',$ts,GETDATE())"
        $cmd = $conn.CreateCommand(); $cmd.CommandText = $sql; $cmd.ExecuteNonQuery() | Out-Null
    }
    Write-Output "  $deviceId : 200 GPS records inserted"
}

Write-Output ""
Write-Output "=== Posture Records (100 records x 3 animals) ==="
$postureTypes = @("站立", "行走", "躺卧", "采食", "奔跑")
$postureWeights = @(30, 30, 20, 15, 5)

foreach ($deviceId in $animals) {
    for ($i = 0; $i -lt 100; $i++) {
        $ts = [long]($startUtcTicks + $i * 10 * 60 * 1000)
        $cumWeight = 0; $rand = $rng.Next(1, 101)
        $pt = "站立"
        for ($p = 0; $p -lt $postureTypes.Count; $p++) {
            $cumWeight += $postureWeights[$p]
            if ($rand -le $cumWeight) { $pt = $postureTypes[$p]; break }
        }
        $conf = [Math]::Round(0.65 + $rng.NextDouble() * 0.34, 2)
        $ax = [Math]::Round(($rng.NextDouble() - 0.5) * 2.0, 3)
        $ay = [Math]::Round(($rng.NextDouble() - 0.5) * 2.0, 3)
        $az = [Math]::Round(0.8 + $rng.NextDouble() * 1.2, 3)
        $gx = [Math]::Round(($rng.NextDouble() - 0.5) * 0.5, 3)
        $gy = [Math]::Round(($rng.NextDouble() - 0.5) * 0.5, 3)
        $gz = [Math]::Round(($rng.NextDouble() - 0.5) * 0.5, 3)
        $lying = if ($pt -eq "躺卧") { $rng.Next(300, 1800) } else { 0 }
        $standing = if ($pt -eq "站立" -or $pt -eq "采食") { $rng.Next(60, 600) } else { 0 }
        $tilt = if ($pt -eq "躺卧") { [Math]::Round(85 + $rng.NextDouble() * 5, 1) } else { [Math]::Round($rng.NextDouble() * 15, 1) }
        $accelMag = [Math]::Round([Math]::Sqrt($ax*$ax + $ay*$ay + $az*$az), 3)
        $dtStr = [DateTime]::FromBinary($ts * 10000).ToString("yyyy-MM-ddTHH:mm:ss")
        $sql = "INSERT INTO posture_results (device_id,animal_id,posture_type,confidence,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z,timestamp,lying_duration,standing_duration,tilt_angle,accel_magnitude,confidence_score,create_time) VALUES ('$deviceId','$deviceId','$pt',$conf,$ax,$ay,$az,$gx,$gy,$gz,$ts,$lying,$standing,$tilt,$accelMag,$conf,'$dtStr')"
        $cmd = $conn.CreateCommand(); $cmd.CommandText = $sql; $cmd.ExecuteNonQuery() | Out-Null
    }
    Write-Output "  $deviceId : 100 posture records inserted"
}

Write-Output ""
Write-Output "=== Behavior Results (50 records x 3 animals) ==="
$behaviorTypes = @("采食", "站立", "行走", "躺卧休息", "奔跑")

foreach ($deviceId in $animals) {
    $base = $baseCoords[$deviceId]
    for ($i = 0; $i -lt 50; $i++) {
        $bt = $behaviorTypes[$rng.Next(0, $behaviorTypes.Count)]
        $conf = [Math]::Round(0.60 + $rng.NextDouble() * 0.38, 2)
        $duration = $rng.Next(30, 1800)
        $startOffset = $rng.Next(0, 1440)
        $startDt = $todayStart.AddMinutes($startOffset)
        $startStr = $startDt.ToString("yyyy-MM-ddTHH:mm:ss")
        $endStr = $startDt.AddSeconds($duration).ToString("yyyy-MM-ddTHH:mm:ss")
        $lat = [Math]::Round($base.lat + ($rng.NextDouble() - 0.5) * 0.002, 6)
        $lng = [Math]::Round($base.lng + ($rng.NextDouble() - 0.5) * 0.002, 6)
        $chewing = if ($bt -eq "采食") { $rng.Next(10, 80) } else { $rng.Next(0, 5) }
        $jawRate = if ($bt -eq "采食") { [Math]::Round(0.5 + $rng.NextDouble() * 1.5, 2) } else { [Math]::Round($rng.NextDouble() * 0.3, 2) }
        $activityLvl = [Math]::Round($rng.NextDouble(), 2)
        $sql = "INSERT INTO behavior_results (animal_id,behavior_type,confidence_score,location_lat,location_lng,duration_seconds,start_time,end_time,chewing_count,jaw_movement_rate,activity_level,data_modality,model_type,create_time) VALUES ('$deviceId','$bt',$conf,$lat,$lng,$duration,'$startStr','$endStr',$chewing,$jawRate,$activityLvl,'多模态','CNN+LSTM','$($startDt.ToString('yyyy-MM-ddTHH:mm:ss'))')"
        $cmd = $conn.CreateCommand(); $cmd.CommandText = $sql; $cmd.ExecuteNonQuery() | Out-Null
    }
    Write-Output "  $deviceId : 50 behavior records inserted"
}

Write-Output ""
Write-Output "=== Location Tracks (100 records x 3 animals) ==="
foreach ($deviceId in $animals) {
    $base = $baseCoords[$deviceId]
    for ($i = 0; $i -lt 100; $i++) {
        $ts = [long]($startUtcTicks + $i * 15 * 60 * 1000)
        $lat = [Math]::Round($base.lat + ($rng.NextDouble() - 0.5) * 0.002, 6)
        $lng = [Math]::Round($base.lng + ($rng.NextDouble() - 0.5) * 0.002, 6)
        $speed = [Math]::Round($rng.NextDouble() * 4.0, 2)
        $pt = $postureTypes[$rng.Next(0, $postureTypes.Count)]
        $battery = $rng.Next(50, 100)
        $direction = [Math]::Round($rng.NextDouble() * 360, 1)
        $gpsQuality = if ($rng.NextDouble() -gt 0.1) { "fix" } else { "float" }
        $isValid = if ($rng.NextDouble() -gt 0.05) { 1 } else { 0 }
        $acc = [Math]::Round(1.0 + $rng.NextDouble() * 3.0, 1)
        $sat = $rng.Next(5, 12)
        $signal = $rng.Next(-90, -60)
        $dtStr = [DateTime]::FromBinary($ts * 10000).ToString("yyyy-MM-ddTHH:mm:ss")
        $sql = "INSERT INTO location_tracks (device_id,animal_id,longitude,latitude,speed,posture,timestamp,battery_level,direction,gps_quality,is_valid_gps,position_accuracy,satellite_count,signal_strength,create_time) VALUES ('$deviceId','$deviceId',$lng,$lat,$speed,'$pt',$ts,$battery,$direction,'$gpsQuality',$isValid,$acc,$sat,$signal,'$dtStr')"
        $cmd = $conn.CreateCommand(); $cmd.CommandText = $sql; $cmd.ExecuteNonQuery() | Out-Null
    }
    Write-Output "  $deviceId : 100 location track records inserted"
}

Write-Output ""
Write-Output "=== Health Status (1 record x 3 animals) ==="
foreach ($deviceId in $animals) {
    $temp = [Math]::Round(37.5 + ($rng.NextDouble() - 0.5) * 1.0, 1)
    $hr = $rng.Next(60, 90)
    $rr = $rng.Next(15, 30)
    $feeding = $rng.Next(120, 480)
    $walking = $rng.Next(60, 300)
    $resting = $rng.Next(180, 600)
    $alertLevel = if ($rng.NextDouble() -gt 0.85) { "warning" } else { "normal" }
    $overall = if ($alertLevel -eq "warning") { "concern" } else { "healthy" }
    $tsStr = $now.AddHours($rng.NextDouble() * 12).ToString("yyyy-MM-ddTHH:mm:ss")
    $sql = "INSERT INTO health_status (animal_id,body_temperature,heart_rate,respiratory_rate,alert_level,alert_message,alert_type,feeding_duration,walking_duration,resting_duration,behavior_status,overall_status,hr_status,rr_status,temp_status,location_status,timestamp,is_resolved) VALUES ('$deviceId',$temp,$hr,$rr,'$alertLevel','牛只健康状态正常','HEALTH_CHECK',$feeding,$walking,$resting,'正常活动','$overall','normal','normal','normal','定位正常','$tsStr',0)"
    $cmd = $conn.CreateCommand(); $cmd.CommandText = $sql; $cmd.ExecuteNonQuery() | Out-Null
    Write-Output "  $deviceId : health status inserted"
}

Write-Output ""
Write-Output "=== Step Counts (20 records x 3 animals) ==="
foreach ($deviceId in $animals) {
    for ($i = 0; $i -lt 20; $i++) {
        $ts = $todayStart.AddMinutes($i * 60).ToString("yyyy-MM-ddTHH:mm:ss")
        $steps = $rng.Next(50, 500)
        $daily = $rng.Next(3000, 15000)
        $walkingDist = [Math]::Round($rng.NextDouble() * 5.0, 2)
        $activeDur = $rng.Next(60, 480)
        $stepFreq = [Math]::Round(60 + $rng.NextDouble() * 40, 1)
        $avgStepLen = [Math]::Round(0.3 + $rng.NextDouble() * 0.3, 2)
        $activity = if ($steps -gt 300) { "high" } elseif ($steps -gt 150) { "medium" } else { "low" }
        $sql = "INSERT INTO step_counts (device_id,animal_id,timestamp,steps,daily_steps,walking_distance,active_duration,step_frequency,avg_step_length,activity_level,create_time) VALUES ('$deviceId','$deviceId','$ts',$steps,$daily,$walkingDist,$activeDur,$stepFreq,$avgStepLen,'$activity','$ts')"
        $cmd = $conn.CreateCommand(); $cmd.CommandText = $sql; $cmd.ExecuteNonQuery() | Out-Null
    }
    Write-Output "  $deviceId : 20 step count records inserted"
}

Write-Output ""
Write-Output "=== Daily Step Summary (7 days x 3 animals) ==="
foreach ($deviceId in $animals) {
    foreach ($offset in -6..0) {
        $d = $now.AddDays($offset).ToString("yyyy-MM-dd")
        $total = $rng.Next(4000, 18000)
        $dist = [Math]::Round($total * 0.0004 + $rng.NextDouble() * 0.5, 2)
        $active = [Math]::Round($total / 30.0 + $rng.NextDouble() * 10, 0)
        $goal = 12000
        $rate = [Math]::Round($total / $goal * 100.0, 1)
        $sql = "INSERT INTO daily_step_summary (device_id,animal_id,date,total_steps,walking_distance,active_minutes,goal_steps,completion_rate,created_at) VALUES ('$deviceId','$deviceId','$d',$total,$dist,$active,$goal,$rate,GETDATE())"
        $cmd = $conn.CreateCommand(); $cmd.CommandText = $sql; $cmd.ExecuteNonQuery() | Out-Null
    }
    Write-Output "  $deviceId : 7 daily summaries inserted"
}

Write-Output ""
Write-Output "=== Sensor Data (50 records x 3 animals) ==="
foreach ($deviceId in $animals) {
    $base = $baseCoords[$deviceId]
    for ($i = 0; $i -lt 50; $i++) {
        $ts = [long]($startUtcTicks + $i * 30 * 60 * 1000)
        $ax = [Math]::Round(($rng.NextDouble() - 0.5) * 2.0, 3)
        $ay = [Math]::Round(($rng.NextDouble() - 0.5) * 2.0, 3)
        $az = [Math]::Round(0.8 + $rng.NextDouble() * 1.2, 3)
        $pt = $postureTypes[$rng.Next(0, $postureTypes.Count)]
        $sc = $rng.Next(0, 200)
        $lat = [Math]::Round($base.lat + ($rng.NextDouble() - 0.5) * 0.002, 6)
        $lng = [Math]::Round($base.lng + ($rng.NextDouble() - 0.5) * 0.002, 6)
        $hr = $rng.Next(55, 95)
        $postureConf = [Math]::Round(0.7 + $rng.NextDouble() * 0.28, 2)
        $temp = [Math]::Round(15 + $rng.NextDouble() * 10, 1)
        $battery = $rng.Next(50, 100)
        $sql = "INSERT INTO sensor_data (device_id,animal_id,accelx,accely,accelz,posture_type,step_count,timestamp,latitude,longitude,heart_rate,posture_confidence,temperature,battery_level,is_valid,data_source,create_time) VALUES ('$deviceId','$deviceId',$ax,$ay,$az,'$pt',$sc,$ts,$lat,$lng,$hr,$postureConf,$temp,$battery,1,'DEVICE','$([DateTime]::FromBinary($ts * 10000).ToString('yyyy-MM-ddTHH:mm:ss'))')"
        $cmd = $conn.CreateCommand(); $cmd.CommandText = $sql; $cmd.ExecuteNonQuery() | Out-Null
    }
    Write-Output "  $deviceId : 50 sensor data records inserted"
}

# Final counts
Write-Output ""
Write-Output "=== Final Record Counts ==="
$cmd = $conn.CreateCommand()
$tables = @("gps_locations", "posture_results", "behavior_results", "location_tracks", "health_status", "step_counts", "daily_step_summary", "sensor_data")
foreach ($t in $tables) {
    $cmd.CommandText = "SELECT COUNT(*) FROM $t WHERE animal_id IN ('7249','7250','7251')"
    $n = $cmd.ExecuteScalar()
    Write-Output "  $t : $n"
}

$conn.Close()
Write-Output ""
Write-Output "=== Done ==="

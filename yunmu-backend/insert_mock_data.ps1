# ============================================================================
# 离线演示数据生成脚本 - 高原牛羊行为AI监测系统
# 覆盖个体（项圈编号，本系统不以名字标识动物）: 7249, 7250, 7251
#
# 定位说明（重要）：
#   本脚本【直接写库】，与「后端设备数据模拟器」是两条不同路线：
#     - 后端模拟器（推荐）：POST /api/simulator/start 或 /api/simulator/backfill，
#       数据经 MQTT 消息处理入口 → Python 推理 → 回调入库，是完整链路的真实产物；
#     - 本脚本：不经过任何链路，仅用于「离线环境无法启动后端 / Python 服务」时快速铺底。
#   正式材料中，本脚本产出的数据不得表述为真实采集数据；优先使用模拟器。
#
# 表结构对齐说明：
#   数据库由 JPA（spring.jpa.hibernate.ddl-auto=update）按实体类自动建表，
#   因此插入列必须与实体字段一致，否则执行会报「列名无效」。已对齐：
#     sensor_data / posture_results / behavior_results /
#     location_tracks / health_status / step_counts
#   已跳过：gps_locations / daily_step_summary（无对应实体，见 $includeLegacyTables）
# ============================================================================

$connStr = "Server=localhost,1433;Database=yunmu_db;User Id=sa;Password=1234567890;TrustServerCertificate=True;"
$conn = New-Object System.Data.SqlClient.SqlConnection($connStr)
$conn.Open()

# 是否生成「非 JPA 实体表」的数据（gps_locations / daily_step_summary）。
# 这两张表没有对应实体，数据库重建后不会被自动创建，且代码中无任何读取点，默认跳过。
# 如确有需要，请先手工建表，再改为 $true。
$includeLegacyTables = $false

function Invoke-Sql($sql, $desc) {
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = $sql
    try {
        $cmd.ExecuteNonQuery() | Out-Null
        Write-Output "  [OK]  $desc"
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

$deviceCodes = @("7249", "7250", "7251")
$now = Get-Date
$today = $now.ToString("yyyy-MM-dd")
$todayStart = [DateTime]::Parse($today)
$nowUtcTicks = $now.ToUniversalTime().Ticks / 10000
$startUtcTicks = $todayStart.ToUniversalTime().Ticks / 10000
$rng = New-Object System.Random(42)

Write-Output "=== 清理旧的演示数据 ==="
if ($includeLegacyTables) {
    Invoke-Sql "DELETE FROM gps_locations WHERE animal_id IN ('7249','7250','7251')" "gps_locations cleared"
}
Invoke-Sql "DELETE FROM posture_results WHERE animal_id IN ('7249','7250','7251')" "posture_results cleared"
Invoke-Sql "DELETE FROM behavior_results WHERE animal_id IN ('7249','7250','7251')" "behavior_results cleared"
Invoke-Sql "DELETE FROM location_tracks WHERE animal_id IN ('7249','7250','7251')" "location_tracks cleared"
Invoke-Sql "DELETE FROM health_status WHERE animal_id IN ('7249','7250','7251')" "health_status cleared"
Invoke-Sql "DELETE FROM step_counts WHERE animal_id IN ('7249','7250','7251')" "step_counts cleared"
if ($includeLegacyTables) {
    Invoke-Sql "DELETE FROM daily_step_summary WHERE animal_id IN ('7249','7250','7251')" "daily_step_summary cleared"
}
Invoke-Sql "DELETE FROM sensor_data WHERE animal_id IN ('7249','7250','7251')" "sensor_data cleared"

# ---------------------------------------------------------------------------
# GPS 定位记录（非 JPA 实体表，默认跳过）
# ---------------------------------------------------------------------------
if ($includeLegacyTables) {
    Write-Output ""
    Write-Output "=== GPS Locations (200 records x 3 animals) ==="
    foreach ($deviceId in $deviceCodes) {
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
} else {
    Write-Output ""
    Write-Output "=== GPS Locations 已跳过（gps_locations 无 JPA 实体，重建后不存在） ==="
}

Write-Output ""
Write-Output "=== Posture Records (100 records x 3 animals) ==="
$postureTypes = @("standing", "walking", "lying", "feeding", "running")
$postureWeights = @(30, 30, 20, 15, 5)

foreach ($deviceId in $deviceCodes) {
    for ($i = 0; $i -lt 100; $i++) {
        $ts = [long]($startUtcTicks + $i * 10 * 60 * 1000)
        $cumWeight = 0; $rand = $rng.Next(1, 101)
        $pt = "standing"
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
        $lying = if ($pt -eq "lying") { $rng.Next(300, 1800) } else { 0 }
        $standing = if ($pt -eq "standing" -or $pt -eq "feeding") { $rng.Next(60, 600) } else { 0 }
        $tilt = if ($pt -eq "lying") { [Math]::Round(85 + $rng.NextDouble() * 5, 1) } else { [Math]::Round($rng.NextDouble() * 15, 1) }
        $accelMag = [Math]::Round([Math]::Sqrt($ax*$ax + $ay*$ay + $az*$az), 3)
        $dtStr = [DateTime]::FromBinary($ts * 10000).ToString("yyyy-MM-ddTHH:mm:ss")
        # 注意：posture_results 只有 confidence_score 一列，没有 confidence
        $sql = "INSERT INTO posture_results (device_id,animal_id,posture_type,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z,timestamp,lying_duration,standing_duration,tilt_angle,accel_magnitude,confidence_score,create_time) VALUES ('$deviceId','$deviceId','$pt',$ax,$ay,$az,$gx,$gy,$gz,$ts,$lying,$standing,$tilt,$accelMag,$conf,'$dtStr')"
        $cmd = $conn.CreateCommand(); $cmd.CommandText = $sql; $cmd.ExecuteNonQuery() | Out-Null
    }
    Write-Output "  $deviceId : 100 posture records inserted"
}

Write-Output ""
Write-Output "=== Behavior Results (50 records x 3 animals) ==="
$behaviorTypes = @("feeding", "standing", "walking", "lying", "running")

foreach ($deviceId in $deviceCodes) {
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
        $chewing = if ($bt -eq "feeding") { $rng.Next(10, 80) } else { $rng.Next(0, 5) }
        $jawRate = if ($bt -eq "feeding") { [Math]::Round(0.5 + $rng.NextDouble() * 1.5, 2) } else { [Math]::Round($rng.NextDouble() * 0.3, 2) }
        $activityLvl = [Math]::Round($rng.NextDouble(), 2)
        # data_modality / model_type 与链路真实取值一致（加速度模态 + 规则引擎），不得写成 CNN+LSTM
        $sql = "INSERT INTO behavior_results (animal_id,behavior_type,confidence_score,location_lat,location_lng,duration_seconds,start_time,end_time,chewing_count,jaw_movement_rate,activity_level,data_modality,model_type,create_time) VALUES ('$deviceId','$bt',$conf,$lat,$lng,$duration,'$startStr','$endStr',$chewing,$jawRate,$activityLvl,'accel','rule_based','$($startDt.ToString('yyyy-MM-ddTHH:mm:ss'))')"
        $cmd = $conn.CreateCommand(); $cmd.CommandText = $sql; $cmd.ExecuteNonQuery() | Out-Null
    }
    Write-Output "  $deviceId : 50 behavior records inserted"
}

Write-Output ""
Write-Output "=== Location Tracks (100 records x 3 animals) ==="
foreach ($deviceId in $deviceCodes) {
    $base = $baseCoords[$deviceId]
    for ($i = 0; $i -lt 100; $i++) {
        $ts = [long]($startUtcTicks + $i * 15 * 60 * 1000)
        $lat = [Math]::Round($base.lat + ($rng.NextDouble() - 0.5) * 0.002, 6)
        $lng = [Math]::Round($base.lng + ($rng.NextDouble() - 0.5) * 0.002, 6)
        $speed = [Math]::Round($rng.NextDouble() * 4.0, 2)
        $battery = $rng.Next(50, 100)
        $direction = [Math]::Round($rng.NextDouble() * 360, 1)
        $gpsQuality = if ($rng.NextDouble() -gt 0.1) { "fix" } else { "float" }
        $isValid = if ($rng.NextDouble() -gt 0.05) { 1 } else { 0 }
        $acc = [Math]::Round(1.0 + $rng.NextDouble() * 3.0, 1)
        $sat = $rng.Next(5, 12)
        $signal = $rng.Next(-90, -60)
        $dtStr = [DateTime]::FromBinary($ts * 10000).ToString("yyyy-MM-ddTHH:mm:ss")
        # 注意：location_tracks 实体没有 posture 列，姿态信息不在本表
        $sql = "INSERT INTO location_tracks (device_id,animal_id,longitude,latitude,speed,timestamp,battery_level,direction,gps_quality,is_valid_gps,position_accuracy,satellite_count,signal_strength,create_time) VALUES ('$deviceId','$deviceId',$lng,$lat,$speed,$ts,$battery,$direction,'$gpsQuality',$isValid,$acc,$sat,$signal,'$dtStr')"
        $cmd = $conn.CreateCommand(); $cmd.CommandText = $sql; $cmd.ExecuteNonQuery() | Out-Null
    }
    Write-Output "  $deviceId : 100 location track records inserted"
}

Write-Output ""
Write-Output "=== Health Status (1 record x 3 animals) ==="
foreach ($deviceId in $deviceCodes) {
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
foreach ($deviceId in $deviceCodes) {
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

# ---------------------------------------------------------------------------
# 每日步数汇总（非 JPA 实体表，默认跳过）
# ---------------------------------------------------------------------------
if ($includeLegacyTables) {
    Write-Output ""
    Write-Output "=== Daily Step Summary (7 days x 3 animals) ==="
    foreach ($deviceId in $deviceCodes) {
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
} else {
    Write-Output ""
    Write-Output "=== Daily Step Summary 已跳过（daily_step_summary 无 JPA 实体，重建后不存在） ==="
}

Write-Output ""
Write-Output "=== Sensor Data (50 records x 3 animals) ==="
foreach ($deviceId in $deviceCodes) {
    $base = $baseCoords[$deviceId]
    for ($i = 0; $i -lt 50; $i++) {
        $ts = [long]($startUtcTicks + $i * 30 * 60 * 1000)
        # 注意：实体字段 accelX/accelY/accelZ 实际生成的列名为 accelx/accely/accelz（无下划线），
        # 已按 Hibernate 建表结果核对，勿改为 accel_x
        $ax = [Math]::Round(($rng.NextDouble() - 0.5) * 2.0, 3)
        $ay = [Math]::Round(($rng.NextDouble() - 0.5) * 2.0, 3)
        $az = [Math]::Round(0.8 + $rng.NextDouble() * 1.2, 3)
        $pt = $postureTypes[$rng.Next(0, $postureTypes.Count)]
        $sc = $rng.Next(0, 200)
        $lat = [Math]::Round($base.lat + ($rng.NextDouble() - 0.5) * 0.002, 6)
        $lng = [Math]::Round($base.lng + ($rng.NextDouble() - 0.5) * 0.002, 6)
        $hr = $rng.Next(55, 95)
        $postureConf = [Math]::Round(0.7 + $rng.NextDouble() * 0.28, 2)
        $temp = [Math]::Round(38 + $rng.NextDouble() * 1.5, 1)
        $battery = $rng.Next(50, 100)
        $sql = "INSERT INTO sensor_data (device_id,animal_id,accel_x,accel_y,accel_z,posture_type,step_count,timestamp,latitude,longitude,heart_rate,posture_confidence,temperature,battery_level,is_valid,data_source,create_time) VALUES ('$deviceId','$deviceId',$ax,$ay,$az,'$pt',$sc,$ts,$lat,$lng,$hr,$postureConf,$temp,$battery,1,'DEVICE','$([DateTime]::FromBinary($ts * 10000).ToString('yyyy-MM-ddTHH:mm:ss'))')"
        $cmd = $conn.CreateCommand(); $cmd.CommandText = $sql; $cmd.ExecuteNonQuery() | Out-Null
    }
    Write-Output "  $deviceId : 50 sensor data records inserted"
}

# Final counts
Write-Output ""
Write-Output "=== Final Record Counts ==="
$cmd = $conn.CreateCommand()
$tables = @("posture_results", "behavior_results", "location_tracks", "health_status", "step_counts", "sensor_data")
foreach ($t in $tables) {
    $cmd.CommandText = "SELECT COUNT(*) FROM $t WHERE animal_id IN ('7249','7250','7251')"
    $n = $cmd.ExecuteScalar()
    Write-Output "  $t : $n"
}

$conn.Close()
Write-Output ""
Write-Output "=== Done ==="

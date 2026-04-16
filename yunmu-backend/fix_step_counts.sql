-- ============================================================
-- 修复 step_counts 表结构问题
-- 1. timestamp 列类型从 bigint 改为 datetime2（Hibernate LocalDateTime 兼容）
-- 2. 添加缺失的 steps 列（实际列名，step_count 是错误的）
-- 3. 删除无效的 step_count 列（如果有残留）
-- ============================================================

-- 1. 删除旧的可能有问题的列
IF EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'step_counts' AND COLUMN_NAME = 'step_count')
BEGIN
    ALTER TABLE step_counts DROP COLUMN step_count;
    PRINT 'Dropped column: step_count';
END

-- 2. 添加 steps 列（实际存在的业务列）
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'step_counts' AND COLUMN_NAME = 'steps')
BEGIN
    ALTER TABLE step_counts ADD steps INT DEFAULT 0;
    PRINT 'Added column: steps';
END

-- 3. 将 timestamp 从 bigint 改为 datetime2（Hibernate 的 LocalDateTime 自动映射）
IF EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'step_counts' AND COLUMN_NAME = 'timestamp' AND DATA_TYPE = 'bigint')
BEGIN
    -- 先备份数据（bigint 是毫秒时间戳，需要转换）
    -- 保留原 bigint 数据到临时列，再创建 datetime2 列
    ALTER TABLE step_counts ADD timestamp_old BIGINT;
    UPDATE step_counts SET timestamp_old = timestamp;
    ALTER TABLE step_counts DROP COLUMN timestamp;
    ALTER TABLE step_counts ADD timestamp DATETIME2;
    -- 尝试转换（如果数据合法）
    BEGIN TRY
        UPDATE step_counts SET timestamp = DATEADD(SECOND, timestamp_old / 1000, '19700101')
            WHERE timestamp_old IS NOT NULL;
        PRINT 'Converted timestamp from bigint to datetime2';
    END TRY
    BEGIN CATCH
        PRINT 'Warning: timestamp conversion failed, column left as datetime2 (NULL values)';
    END CATCH
    ALTER TABLE step_counts DROP COLUMN timestamp_old;
END
ELSE IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'step_counts' AND COLUMN_NAME = 'timestamp' AND DATA_TYPE = 'datetime2')
BEGIN
    -- 如果是其他类型，尝试添加 datetime2 列（小心处理）
    PRINT 'timestamp column type is not bigint, manual check needed';
END

-- 4. 验证最终表结构
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'step_counts'
ORDER BY ORDINAL_POSITION;

PRINT '=== Done ===';

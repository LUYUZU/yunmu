# 云牧智感 - 运行指南

## 环境要求

- Python 3.8-3.11 (Python 3.12+ 可能存在兼容性问题)
- Java 17+
- MySQL 8.0+
- Redis 6.0+

## 快速开始

### 1. 安装 Python 依赖

```bash
cd yunmu-python

# 如果 pip 安装失败，使用国内镜像
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
```

### 2. 训练模型

```bash
python train.py
```

### 3. 运行 Python 服务

```bash
python run.py
```

服务将在 http://localhost:5000 启动

### 4. 运行 Java 后端

```bash
cd yunmu-backend

# 确保 MySQL 已启动并创建数据库
# mysql -u root -p
# CREATE DATABASE yunmu_db;

# 修改 application.yml 中的数据库配置

# 运行
mvn spring-boot:run
```

服务将在 http://localhost:8080 启动

## 测试

访问以下接口测试：

- Python 健康检查：http://localhost:5000/health
- Java 后端：http://localhost:8080/swagger-ui.html

## 常见问题

### Python 版本过高

如果使用 Python 3.12+，某些包可能不兼容。建议使用 Python 3.10 或 3.11。

### 数据库连接失败

确保 MySQL 服务已启动，并创建数据库：
```sql
CREATE DATABASE yunmu_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 端口被占用

修改配置文件中的端口：
- Python: 编辑 .env 文件，修改 PORT
- Java: 编辑 application.yml，修改 server.port

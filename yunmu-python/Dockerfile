# Dockerfile
FROM python:3.9-slim

# 设置工作目录
WORKDIR /app

# 设置环境变量
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# 安装系统依赖
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

# 复制依赖文件
COPY requirements_conda.txt .

# 安装Python依赖
RUN pip install --no-cache-dir -r requirements_conda.txt

# 复制应用代码
COPY . .

# 创建目录
RUN mkdir -p logs data/raw data/processed models/trained

# 暴露端口
EXPOSE 5000

# 运行应用
CMD ["python", "run.py", "--host", "0.0.0.0", "--port", "5000"]
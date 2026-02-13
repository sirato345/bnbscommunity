# 使用 Python 3.12 的官方 slim 镜像
FROM python:3.12-slim

# 设置工作目录
WORKDIR /app

# 设置环境变量（与 fly.toml 保持一致）
ENV PORT=8080
ENV TZ=Asia/Tokyo
ENV PYTHONUNBUFFERED=true
ENV PYTHONDONTWRITEBYTECODE=true

# 暴露端口（与 fly.toml 的 internal_port 一致）
EXPOSE 8080

# 创建 csv 目录
RUN mkdir -p /app/csv

# 复制 CSV 文件
COPY csv/*.csv /app/csv/

# 复制当前目录所有文件到容器中
COPY . .
RUN pip install fastapi uvicorn

# 安装依赖（如果有 requirements.txt）
RUN if [ -f requirements.txt ]; then pip install --no-cache-dir -r requirements.txt; fi

# 运行应用
# 假设你的主文件是 main.py，请根据实际情况修改
CMD ["python", "main.py"]
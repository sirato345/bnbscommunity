# ─── ビルドステージ ──────────────────────────
FROM python:3.12-slim AS builder

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc g++ \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
# pandas-ta は依存競合のため --no-deps で別途インストール
RUN pip install --no-cache-dir pandas-ta==0.4.71b0 --no-deps


# ─── 実行ステージ ────────────────────────────
FROM python:3.12-slim

WORKDIR /app

ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1
ENV DJANGO_SETTINGS_MODULE=config.settings
ENV PORT=8080

COPY --from=builder /usr/local/lib/python3.12/site-packages /usr/local/lib/python3.12/site-packages
COPY --from=builder /usr/local/bin /usr/local/bin

COPY . .

RUN mkdir -p /app/csv
COPY csv/ /app/csv/

EXPOSE 8080

CMD ["gunicorn", "config.wsgi:application", \
     "--bind", "0.0.0.0:8080", \
     "--workers", "1", \
     "--threads", "8", \
     "--timeout", "120", \
     "--log-level", "info"]

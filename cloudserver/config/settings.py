"""
config/settings.py
"""
from pathlib import Path
import os

BASE_DIR = Path(__file__).resolve().parent.parent

SECRET_KEY    = os.environ.get("SECRET_KEY", "django-insecure-changeme-in-production")
# DEBUG         = os.environ.get("DEBUG", "False") == "True"
DEBUG = True   # 一時的に直接 True に変更
ALLOWED_HOSTS = ["*"]

APPEND_SLASH = False  # 允许不带斜杠的 URL，否则URL 末尾必须带斜杠，访问 /csv 会被重定向到 /csv/，导致前端请求失败

INSTALLED_APPS = [
    "django.contrib.contenttypes",
    "django.contrib.staticfiles",
    "django.contrib.auth",
    "rest_framework",
    "corsheaders",
]

MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",   # 最上位に置く
    "django.middleware.common.CommonMiddleware",
]

ROOT_URLCONF     = "config.urls"
WSGI_APPLICATION = "config.wsgi.application"

# DB不要（このプロジェクトはファイルベース）
# 変更後
DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.dummy",
    }
}

STATIC_URL = "/static/"

# ─── CORS ───────────────────────────────────
CORS_ALLOWED_ORIGINS = [
    "http://localhost:3000",
    "http://192.168.3.9:3000",
    "http://godseye.bnbscommunity.com",
    "http://www.godseye.bnbscommunity.com",
    "http://bnbchain.bnbscommunity.com",
    "http://www.bnbchain.bnbscommunity.com",
    "http://bnbscommunity.com",
    "http://www.bnbscommunity.com",
    "https://localhost:3000",
    "https://192.168.3.9:3000",
    "https://godseye.bnbscommunity.com",
    "https://www.godseye.bnbscommunity.com",
    "https://bnbchain.bnbscommunity.com",
    "https://www.bnbchain.bnbscommunity.com",
    "https://bnbscommunity.com",
    "https://www.bnbscommunity.com",
]
CORS_ALLOW_CREDENTIALS = True
CORS_ALLOW_METHODS     = ["GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"]
CORS_ALLOW_HEADERS     = [
    "Content-Type",
    "Authorization",
    "X-CSRF-Token",
    "Access-Control-Allow-Origin",
]

# ─── DRF ────────────────────────────────────
REST_FRAMEWORK = {
    "DEFAULT_RENDERER_CLASSES": ["rest_framework.renderers.JSONRenderer"],
    "DEFAULT_PARSER_CLASSES": [
        "rest_framework.parsers.JSONParser",
        "rest_framework.parsers.MultiPartParser",
    ],
}

# ─── アプリ固有設定 ──────────────────────────
CSV_DIR          = BASE_DIR / "csv"
TOTAL_BNBS_COUNT = 21_000_000
KLINE_LIMIT      = 100
CACHE_DURATION   = 10   # 秒


# GCS 設定
DEFAULT_FILE_STORAGE = "storages.backends.gcloud.GoogleCloudStorage"
GS_BUCKET_NAME = os.environ.get("GS_BUCKET_NAME", "bnbscommunity")
GS_DEFAULT_ACL = None  # 非公開

# config/settings.py
import os

# Binance API 配置 - 从环境变量读取（Cloud Run 会从 Secret Manager 注入）
BINANCE_API_KEY = os.environ.get('BINANCE_API_KEY', '')
BINANCE_API_SECRET = os.environ.get('BINANCE_API_SECRET', '')
BINANCE_TESTNET = os.environ.get('BINANCE_TESTNET', 'true').lower() == 'true'

# 可选：添加启动时的提示
if BINANCE_TESTNET:
    mode = "测试网"
    print(f"🔑 Binance API 已配置 ({mode})")
else:
    mode = "主网"
    print(f"🔑 Binance API 已配置 ({mode})")

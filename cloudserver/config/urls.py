"""
config/urls.py
"""
from apps.csv_manager import get_csv, upload_csv
from django.urls import path

urlpatterns = [
    path("csv",     get_csv,      name="get_csv"),
    path("upload",  upload_csv,   name="upload_csv"),
]

# 使用 name 反向解析 URL
# return redirect('get_csv')  # 重定向到 /csv

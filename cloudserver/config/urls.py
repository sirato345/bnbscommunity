"""
config/urls.py
"""
from django.urls import path
from apps.signals    import get_signals
from apps.csv_manager import get_csv, upload_csv

urlpatterns = [
    path("",        get_signals,  name="signals"),
    path("csv",     get_csv,      name="get_csv"),
    path("upload",  upload_csv,   name="upload_csv"),
]

"""
apps/csv_manager.py
Google Cloud Storage を使用した CSV の取得・アップロード
"""
from __future__ import annotations

import csv
import logging

from django.conf import settings
from google.cloud import storage
from rest_framework.decorators import api_view, parser_classes
from rest_framework.parsers import MultiPartParser
from rest_framework.request import Request
from rest_framework.response import Response

GCS_BUCKET = settings.GS_BUCKET_NAME
GCS_BLOB   = "csv/export-tokenholders-for-contract-0xc07ef1c7af6112c34a110809c6c8efb343e63a64.csv"

logger = logging.getLogger(__name__)

def _get_gcs_client() -> storage.Client:
    """Cloud Storage クライアントを返す（Cloud Run では認証不要）"""
    return storage.Client()


def _download_csv() -> str:
    """GCS から CSV をテキストとしてダウンロードする"""
    client = _get_gcs_client()
    bucket = client.bucket(GCS_BUCKET)
    blob   = bucket.blob(GCS_BLOB)
    return blob.download_as_text(encoding="utf-8-sig")


def _upload_csv(file_obj) -> None:
    """GCS に CSV をアップロードする"""
    client = _get_gcs_client()
    bucket = client.bucket(GCS_BUCKET)
    blob   = bucket.blob(GCS_BLOB)
    blob.upload_from_file(file_obj, rewind=True)


@api_view(["GET"])
def get_csv(request: Request) -> Response:
    """GET /csv — トークンホルダー CSV の一覧を返す"""
    logger.info(f"GCS_BUCKET: {GCS_BUCKET}")
    logger.info(f"GCS_BLOB:   {GCS_BLOB}")

    try:
        content = _download_csv()
    except Exception as e:
        logger.error(f"GCS download failed: {e}")
        return Response({"error": "CSV file not found"}, status=404)

    data   = []
    reader = csv.reader(content.splitlines())
    for i, line in enumerate(reader):
        if i == 0:
            continue    # ヘッダーをスキップ
        count   = int(float(line[1].replace(",", "")))
        percent = (count / settings.TOTAL_BNBS_COUNT) * 100
        data.append([i, line[0], count, f"{percent:.5f}%"])

    return Response(data)


@api_view(["POST"])
@parser_classes([MultiPartParser])
def upload_csv(request: Request) -> Response:
    """POST /upload (multipart/form-data, field name: file)"""
    file_obj = request.FILES.get("file")
    if not file_obj:
        return Response({"error": "file field is required"}, status=400)

    try:
        _upload_csv(file_obj)
    except Exception as e:
        logger.error(f"GCS upload failed: {e}")
        return Response({"error": "Upload failed"}, status=500)

    return Response({"message": "uploaded"})

"""
apps/csv_manager.py
FastAPI の GET /csv と POST /upload を DRF へ移植
"""
from __future__ import annotations

import csv
import shutil

from django.conf import settings
from rest_framework.decorators import api_view, parser_classes
from rest_framework.parsers import MultiPartParser
from rest_framework.request import Request
from rest_framework.response import Response

HOLDER_CSV = (
    settings.CSV_DIR
    / "export-tokenholders-for-contract-0xC07ef1C7af6112C34A110809C6c8Efb343e63A64.csv"
)


@api_view(["GET"])
def get_csv(request: Request) -> Response:
    """GET /csv — トークンホルダー CSV の一覧を返す。"""
    if not HOLDER_CSV.exists():
        return Response({"error": "CSV file not found"}, status=404)

    data = []
    with open(HOLDER_CSV, encoding="utf-8-sig") as f:
        reader = csv.reader(f)
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

    HOLDER_CSV.parent.mkdir(parents=True, exist_ok=True)
    with open(HOLDER_CSV, "wb") as buf:
        shutil.copyfileobj(file_obj, buf)

    return Response({"message": "uploaded"})

#!/bin/bash
# deploy.sh — Cloud Run へのデプロイ手順
set -e

PROJECT_ID="cloudserver"
REGION="asia-northeast1"
REPO="bnbs-repo"
SERVICE="bnbs-django"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/${SERVICE}"

echo "▶ 1. Artifact Registry リポジトリ作成（初回のみ）"
gcloud artifacts repositories create "${REPO}" \
  --repository-format=docker \
  --location="${REGION}" \
  --project="${PROJECT_ID}" 2>/dev/null || echo "既に存在します（スキップ）"

echo "▶ 2. Docker 認証設定"
gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet

echo "▶ 3. Docker ビルド"
docker build -t "${IMAGE}" .

echo "▶ 4. イメージを push"
docker push "${IMAGE}"

echo "▶ 5. Cloud Run へデプロイ"
gcloud run deploy "${SERVICE}" \
  --image "${IMAGE}" \
  --platform managed \
  --region "${REGION}" \
  --port 8080 \
  --memory 1Gi \
  --cpu 1 \
  --min-instances 0 \
  --max-instances 3 \
  --timeout 120 \
  --set-env-vars "DJANGO_SETTINGS_MODULE=config.settings" \
  --allow-unauthenticated \
  --project="${PROJECT_ID}"

echo "✅ デプロイ完了"
gcloud run services describe "${SERVICE}" \
  --region="${REGION}" \
  --format="value(status.url)" \
  --project="${PROJECT_ID}"

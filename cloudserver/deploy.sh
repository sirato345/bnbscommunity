#!/bin/bash

# deploy.sh — Google Cloud Run デプロイスクリプト
# ※※※デスクトップ版のDockerを起動してから、./deploy.shを実行する必要です
# ./deploy.sh で実行する

PROJECT_ID="project-717dce1d-b530-431a-b19"
REGION="asia-northeast1"
REPO="bnbs-repo"
SERVICE="bnbs-django"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/${SERVICE}"

# ─── エラー時に止まるヘルパー ───────────────────
die() {
  echo ""
  echo "❌ [$1] 失敗しました。"
  echo "   詳細: $2"
  echo ""
  read -p "Press Enter to close..."
  exit 1
}

echo "========================================"
echo "  Cloud Run デプロイ開始"
echo "  PROJECT : ${PROJECT_ID}"
echo "  REGION  : ${REGION}"
echo "  SERVICE : ${SERVICE}"
echo "========================================"
echo ""

# ▶ 1. Artifact Registry リポジトリ作成（初回のみ）
echo "▶ 1. Artifact Registry リポジトリ作成（asia-northeast1）"
gcloud artifacts repositories create "${REPO}" \
  --repository-format=docker \
  --location="${REGION}" \
  --project="${PROJECT_ID}" 2>&1 | grep -v "already exists" || true
echo "   完了（既存の場合はスキップ）"
echo ""

# ▶ 2. Docker 認証設定
echo "▶ 2. Docker 認証設定"
gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet \
  || die "Docker認証" "gcloud auth configure-docker が失敗しました"
echo ""

# ▶ 3. Docker ビルド
echo "▶ 3. Docker ビルド"
docker build --no-cache -t "${IMAGE}" . \
  || die "Docker ビルド" "Dockerfile を確認してください"
echo ""

# ▶ 4. イメージを push（最重要：失敗するとデプロイ時にイメージ不在エラーになる）
echo "▶ 4. イメージを Artifact Registry へ push"
docker push "${IMAGE}" \
  || die "Docker push" "push に失敗しました。認証・リポジトリ・ネットワークを確認してください"
echo ""

# push 後にイメージが実際に存在するか確認
echo "▶ 4-確認. push されたイメージを確認"
gcloud artifacts docker images list \
  "${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}" \
  --project="${PROJECT_ID}" \
  --limit=5 \
  || die "イメージ確認" "Registry にイメージが見つかりません"
echo ""

# ▶ 5. Cloud Run へデプロイ
echo "▶ 5. Cloud Run へデプロイ"
gcloud run deploy "${SERVICE}" \
  --image "${IMAGE}" \
  --platform managed \
  --region "${REGION}" \
  --port 8080 \
  --memory 1Gi \
  --cpu 1 \
  --min-instances 1 \
  --max-instances 1 \
  --timeout 300 \
  --set-env-vars "DJANGO_SETTINGS_MODULE=config.settings" \
  --allow-unauthenticated \
  --project="${PROJECT_ID}" \
  || die "Cloud Run デプロイ" "gcloud run deploy が失敗しました"
echo ""

# ▶ 完了
echo "========================================"
echo "✅ デプロイ完了"
echo "   サービスURL:"
gcloud run services describe "${SERVICE}" \
  --region="${REGION}" \
  --format="value(status.url)" \
  --project="${PROJECT_ID}"
echo "========================================"

read -p "Press Enter to close..."
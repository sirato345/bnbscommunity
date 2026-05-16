#!/bin/bash
set -euo pipefail

PROJECT_ID="project-717dce1d-b530-431a-b19"
PROJECT_NUMBER="275599637949"
REGION="asia-northeast1"
REPO="bnbs-repo"
JOB_NAME="trading-job"
SERVICE_ACCOUNT="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
SCHEDULER_SA="scheduler-sa"
SCHEDULER_SA_EMAIL="${SCHEDULER_SA}@${PROJECT_ID}.iam.gserviceaccount.com"
# Cloud Scheduler のサービスエージェント（API有効化時に自動作成）
SCHEDULER_AGENT="service-${PROJECT_NUMBER}@gcp-sa-cloudscheduler.iam.gserviceaccount.com"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/trading-job"
JOB_URL="https://${REGION}-run.googleapis.com/apis/run.googleapis.com/v1/namespaces/${PROJECT_NUMBER}/jobs/${JOB_NAME}:run"

echo "========================================"
echo "  Cloud Run Job デプロイ"
echo "  JOB NAME: ${JOB_NAME}"
echo "========================================"
echo ""

# ─── 1. Artifact Registry 確認 ───────────────
echo "▶ 1. Artifact Registry 確認"
gcloud artifacts repositories describe "${REPO}" \
  --location="${REGION}" \
  --project="${PROJECT_ID}" > /dev/null 2>&1 || \
  gcloud artifacts repositories create "${REPO}" \
    --repository-format=docker \
    --location="${REGION}" \
    --project="${PROJECT_ID}"
echo "   完了"
echo ""

# ─── 2. Firestore権限を設定 ──────────────────
echo "▶ 2. Firestore権限を設定"
gcloud projects add-iam-policy-binding ${PROJECT_ID} \
  --member="serviceAccount:${SERVICE_ACCOUNT}" \
  --role="roles/datastore.user" \
  --condition=None 2>&1 | grep -v "ETag" || true
echo "   完了"
echo ""

# ─── 3. Scheduler SA を作成 ─────────────────
echo "▶ 3. Scheduler サービスアカウントを確認・作成"
if ! gcloud iam service-accounts describe ${SCHEDULER_SA_EMAIL} \
     --project ${PROJECT_ID} &>/dev/null; then
    gcloud iam service-accounts create ${SCHEDULER_SA} \
        --display-name="Cloud Scheduler for Trading Job" \
        --project ${PROJECT_ID}
    echo "   ✅ 作成成功"
else
    echo "   ⚠️  既存のためスキップ"
fi
echo ""

# ─── 4. IAM権限をまとめて付与 ───────────────
echo "▶ 4. IAM権限を付与"

# (a) プロジェクトレベル: scheduler-sa に run.invoker
gcloud projects add-iam-policy-binding ${PROJECT_ID} \
    --member="serviceAccount:${SCHEDULER_SA_EMAIL}" \
    --role="roles/run.invoker" \
    --condition=None 2>&1 | grep -v "ETag" || true
echo "   ✅ (a) プロジェクトレベル run.invoker 付与完了"

# (b) ✅ 修正: Cloud Scheduler サービスエージェントに Token Creator 権限を付与
#    OAuth2トークン生成時に Scheduler Agent が scheduler-sa を impersonate するために必要
#    これがないと UNAUTHENTICATED エラーになる
gcloud iam service-accounts add-iam-policy-binding ${SCHEDULER_SA_EMAIL} \
    --member="serviceAccount:${SCHEDULER_AGENT}" \
    --role="roles/iam.serviceAccountTokenCreator" \
    --project=${PROJECT_ID} 2>&1 | grep -v "ETag" || true
echo "   ✅ (b) Scheduler Agent に TokenCreator 付与完了"
echo ""

# ─── 5. Docker ビルド ────────────────────────
echo "▶ 5. Docker ビルド（Dockerfile.job）"
docker build --no-cache -f Dockerfile.job -t ${IMAGE} .
echo ""

# ─── 6. Artifact Registry へ push ───────────
echo "▶ 6. イメージを Artifact Registry へ push"
docker push ${IMAGE}
echo ""

# ─── 7. 既存のJobを削除 ──────────────────────
echo "▶ 7. 既存のJobがあれば削除"
if gcloud run jobs describe ${JOB_NAME} \
   --region ${REGION} --project ${PROJECT_ID} &>/dev/null; then
    gcloud run jobs delete ${JOB_NAME} \
      --region ${REGION} --project ${PROJECT_ID} --quiet
    echo "   ✅ 削除完了"
else
    echo "   スキップ（既存なし）"
fi
echo ""

# ─── 8. Cloud Run Job を作成 ─────────────────
echo "▶ 8. Cloud Run Job を作成"
gcloud run jobs create ${JOB_NAME} \
  --image ${IMAGE} \
  --region ${REGION} \
  --project ${PROJECT_ID} \
  --memory 1Gi \
  --cpu 1 \
  --task-timeout 300 \
  --max-retries 3 \
  --service-account ${SERVICE_ACCOUNT} \
  --set-env-vars "DJANGO_SETTINGS_MODULE=config.settings" \
  --command "python" \
  --args "aitrading/trading_bot.py"
echo ""

# ─── 8.5. Jobリソースレベルの IAM 権限を付与 ──
echo "▶ 8.5. JobリソースレベルのIAM権限を付与"
# ✅ プロジェクトレベルだけでは不足。Jobリソース自体にも付与が必要。
gcloud run jobs add-iam-policy-binding ${JOB_NAME} \
    --region=${REGION} \
    --project=${PROJECT_ID} \
    --member="serviceAccount:${SCHEDULER_SA_EMAIL}" \
    --role="roles/run.invoker" 2>&1 | grep -v "ETag" || true
echo "   ✅ 完了"
echo ""

# ─── 9. 既存のSchedulerを削除 ────────────────
echo "▶ 9. 既存のSchedulerを削除"
gcloud scheduler jobs delete trading-signal-schedule \
  --location ${REGION} --project ${PROJECT_ID} \
  --quiet 2>/dev/null || true
echo "   完了"
echo ""

# ─── 10. Cloud Scheduler を作成 ──────────────
echo "▶ 10. Cloud Scheduler を作成（3分ごと）"
# ✅ 修正: OIDC ではなく OAuth2 を使用する
#    Cloud Run Admin API（Googleの内部API）の呼び出しには
#    OIDC ではなく OAuth2 が正しい認証方式
#    OIDC を使うと UNAUTHENTICATED になる
gcloud scheduler jobs create http trading-signal-schedule \
  --location ${REGION} \
  --schedule="*/3 * * * *" \
  --uri="${JOB_URL}" \
  --http-method=POST \
  --oauth-service-account-email="${SCHEDULER_SA_EMAIL}" \
  --oauth-token-scope="https://www.googleapis.com/auth/cloud-platform" \
  --headers="Content-Type=application/json" \
  --message-body="{}" \
  --project ${PROJECT_ID}
echo "   ✅ Scheduler作成完了（3分ごとに実行）"
echo ""

# ─── 11. Scheduler 確認 ──────────────────────
echo "▶ 11. Scheduler 確認"
gcloud scheduler jobs describe trading-signal-schedule \
  --location ${REGION} \
  --project ${PROJECT_ID} \
  --format="table(name,schedule,state)"
echo ""

# ─── 12. テスト実行 ──────────────────────────
echo "▶ 12. テスト実行（Job を直接起動）"
echo "   IAM伝播待機（30秒）..."
sleep 30
gcloud run jobs execute ${JOB_NAME} \
  --region ${REGION} \
  --project ${PROJECT_ID} \
  --wait
echo ""

echo "========================================"
echo "✅ デプロイ完了"
echo "   Job:       ${JOB_NAME}"
echo "   Scheduler: trading-signal-schedule (*/3 * * * *)"
echo ""
echo "【確認コマンド】"
echo "  gcloud scheduler jobs list --location=${REGION} --project=${PROJECT_ID}"
echo "  gcloud run jobs executions list --job=${JOB_NAME} --region=${REGION} --project=${PROJECT_ID}"
echo "========================================"

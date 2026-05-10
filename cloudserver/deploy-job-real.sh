#!/bin/bash
set -euo pipefail

PROJECT_ID="project-717dce1d-b530-431a-b19"
PROJECT_NUMBER="275599637949"
REGION="asia-northeast1"
REPO="bnbs-repo"
JOB_NAME="real-trading-bot-job"  # 真实交易Bot的Job名称
SERVICE_ACCOUNT="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
SCHEDULER_SA="scheduler-sa"
SCHEDULER_SA_EMAIL="${SCHEDULER_SA}@${PROJECT_ID}.iam.gserviceaccount.com"
# Cloud Scheduler のサービスエージェント（API有効化時に自動作成）
SCHEDULER_AGENT="service-${PROJECT_NUMBER}@gcp-sa-cloudscheduler.iam.gserviceaccount.com"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/trading-job"
JOB_URL="https://${REGION}-run.googleapis.com/apis/run.googleapis.com/v1/namespaces/${PROJECT_NUMBER}/jobs/${JOB_NAME}:run"

echo "========================================"
echo "  Cloud Run Job デプロイ（真实交易Bot）"
echo "  JOB NAME: ${JOB_NAME}"
echo "  ⚠️  注意：这个Bot会执行真实交易！"
echo "========================================"
echo ""

# deploy-real-job.sh 开头添加安全确认
echo "========================================"
echo "  ⚠️  安全确认"
echo "========================================"
echo ""
echo "即将部署真实交易Bot到 Cloud Run"
echo ""
if [ "${BINANCE_TESTNET:-true}" = "true" ]; then
    echo "当前模式: 🔧 测试网 (Testnet)"
    echo "  - 不会使用真实资金"
    echo "  - 建议先用此模式验证"
else
    echo "当前模式: 💰 主网 (Mainnet)"
    echo "  - 将会使用真实资金！"
    echo "  - 请确保已充分测试"
fi
echo ""
read -p "是否继续部署？(yes/no): " -r CONFIRM
if [[ $CONFIRM != "yes" ]]; then
    echo "部署已取消"
    exit 0
fi
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

# (b) Cloud Scheduler サービスエージェントに Token Creator 権限を付与
gcloud iam service-accounts add-iam-policy-binding ${SCHEDULER_SA_EMAIL} \
    --member="serviceAccount:${SCHEDULER_AGENT}" \
    --role="roles/iam.serviceAccountTokenCreator" \
    --project=${PROJECT_ID} 2>&1 | grep -v "ETag" || true
echo "   ✅ (b) Scheduler Agent に TokenCreator 付与完了"

# (c) Secret Managerアクセス権限を追加（真实交易需要用）
gcloud projects add-iam-policy-binding ${PROJECT_ID} \
    --member="serviceAccount:${SERVICE_ACCOUNT}" \
    --role="roles/secretmanager.secretAccessor" \
    --condition=None 2>&1 | grep -v "ETag" || true
echo "   ✅ (c) Secret Managerアクセス権限付与完了"
echo ""

# ─── 5. Secret Manager に Binance API Key を保存 ─────
echo "▶ 5. Secret Manager 設定（Binance API Key）"

# 環境変数からBinance API Keyを読み込む
if [ -n "${BINANCE_API_KEY:-}" ] && [ -n "${BINANCE_API_SECRET:-}" ]; then
    # binance-api-key を作成または更新
    echo -n "${BINANCE_API_KEY}" | gcloud secrets create binance-api-key \
        --data-file=- \
        --project ${PROJECT_ID} \
        --replication-policy="automatic" 2>/dev/null || \
        echo -n "${BINANCE_API_KEY}" | gcloud secrets versions add binance-api-key \
            --data-file=- --project ${PROJECT_ID}
    
    # binance-api-secret を作成または更新
    echo -n "${BINANCE_API_SECRET}" | gcloud secrets create binance-api-secret \
        --data-file=- \
        --project ${PROJECT_ID} \
        --replication-policy="automatic" 2>/dev/null || \
        echo -n "${BINANCE_API_SECRET}" | gcloud secrets versions add binance-api-secret \
            --data-file=- --project ${PROJECT_ID}
    
    echo "   ✅ Binance API Key を Secret Manager に保存"
    
    # テストネット設定も保存（オプション）
    BINANCE_TESTNET="${BINANCE_TESTNET:-true}"
    echo -n "${BINANCE_TESTNET}" | gcloud secrets create binance-testnet \
        --data-file=- \
        --project ${PROJECT_ID} \
        --replication-policy="automatic" 2>/dev/null || \
        echo -n "${BINANCE_TESTNET}" | gcloud secrets versions add binance-testnet \
            --data-file=- --project ${PROJECT_ID}
    echo "   ✅ テストネット設定: ${BINANCE_TESTNET}"
else
    echo "   ⚠️  Binance API Key が設定されていません"
    echo "   以下のコマンドで設定してください："
    echo "   export BINANCE_API_KEY='your_api_key'"
    echo "   export BINANCE_API_SECRET='your_api_secret'"
    echo "   export BINANCE_TESTNET='true'  # テストネットを使用する場合"
    echo ""
    echo "   または、後で手動でSecret Managerに作成してください"
fi
echo ""

# ─── 6. Docker ビルド ────────────────────────
echo "▶ 6. Docker ビルド（Dockerfile.job）"
docker build --no-cache -f Dockerfile.job -t ${IMAGE} .
echo ""

# ─── 7. Artifact Registry へ push ───────────
echo "▶ 7. イメージを Artifact Registry へ push"
docker push ${IMAGE}
echo ""

# ─── 8. 既存のJobを削除 ──────────────────────
echo "▶ 8. 既存のJobがあれば削除"
if gcloud run jobs describe ${JOB_NAME} \
   --region ${REGION} --project ${PROJECT_ID} &>/dev/null; then
    gcloud run jobs delete ${JOB_NAME} \
      --region ${REGION} --project ${PROJECT_ID} --quiet
    echo "   ✅ 削除完了"
else
    echo "   スキップ（既存なし）"
fi
echo ""

# ─── 9. Cloud Run Job を作成（真实交易） ──────
echo "▶ 9. Cloud Run Job を作成（真实交易Bot）"
echo "   ⚠️  確認: このBotは実際の資金で取引を実行します"
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
  --set-secrets "BINANCE_API_KEY=binance-api-key:latest,BINANCE_API_SECRET=binance-api-secret:latest,BINANCE_TESTNET=binance-testnet:latest" \
  --command "python" \
  --args "aitrading/trading_bot_real.py"
echo "   ✅ Job作成完了"
echo ""

# ─── 10. Jobリソースレベルの IAM 権限を付与 ──
echo "▶ 10. JobリソースレベルのIAM権限を付与"
gcloud run jobs add-iam-policy-binding ${JOB_NAME} \
    --region=${REGION} \
    --project=${PROJECT_ID} \
    --member="serviceAccount:${SCHEDULER_SA_EMAIL}" \
    --role="roles/run.invoker" 2>&1 | grep -v "ETag" || true
echo "   ✅ 完了"
echo ""

# ─── 11. 既存のSchedulerを削除 ────────────────
echo "▶ 11. 既存のSchedulerを削除"
gcloud scheduler jobs delete real-trading-schedule \
  --location ${REGION} --project ${PROJECT_ID} \
  --quiet 2>/dev/null || true
echo "   完了"
echo ""

# ─── 12. Cloud Scheduler を作成 ──────────────
echo "▶ 12. Cloud Scheduler を作成（3分ごと）"
echo "   ⚠️  真实交易执行间隔：3分钟（避免API频率限制）"
gcloud scheduler jobs create http real-trading-schedule \
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

# ─── 13. Scheduler 確認 ──────────────────────
echo "▶ 13. Scheduler 確認"
gcloud scheduler jobs describe real-trading-schedule \
  --location ${REGION} \
  --project ${PROJECT_ID} \
  --format="table(name,schedule,state)"
echo ""

# ─── 14. テスト実行（確認） ──────────────────
echo "▶ 14. テスト実行（手動確認）"
echo "   ⚠️  真实交易のテストを実行しますか？"
echo "   実行すると実際の資金が動く可能性があります"
read -p "   テストを実行する場合は 'yes' を入力してください: " -r TEST_CONFIRM
if [[ $TEST_CONFIRM == "yes" ]]; then
    echo "   IAM伝播待機（30秒）..."
    sleep 30
    gcloud run jobs execute ${JOB_NAME} \
      --region ${REGION} \
      --project ${PROJECT_ID} \
      --wait
    echo "   ✅ テスト実行完了"
else
    echo "   スキップ（テスト実行なし）"
fi
echo ""

echo "========================================"
echo "✅ 真实交易Botデプロイ完了"
echo ""
echo "【デプロイ情報】"
echo "  Job名称:      ${JOB_NAME}"
echo "  Scheduler:    real-trading-schedule (*/3 * * * *)"
echo "  実行間隔:     3分ごと"
echo "  使用スクリプト: trading_bot_real.py"
echo "  データ保存先:  REAL_CURRENT_TRADE / REAL_TRADE_HISTORY"
echo ""
echo "【重要】"
echo "  ⚠️  这个Bot会执行真实交易，请确保："
echo "  1. Binance API Key 权限设置为【仅交易，禁止提现】"
echo "  2. 账户中有足够的USDT余额"
echo "  3. 先用测试网验证（BINANCE_TESTNET=true）"
echo ""
echo "【管理コマンド】"
echo "  実行履歴確認:"
echo "    gcloud run jobs executions list --job=${JOB_NAME} --region=${REGION} --project=${PROJECT_ID}"
echo ""
echo "  一時停止:"
echo "    gcloud scheduler jobs pause real-trading-schedule --location=${REGION} --project=${PROJECT_ID}"
echo ""
echo "  再開:"
echo "    gcloud scheduler jobs resume real-trading-schedule --location=${REGION} --project=${PROJECT_ID}"
echo ""
echo "  Job手動実行:"
echo "    gcloud run jobs execute ${JOB_NAME} --region=${REGION} --project=${PROJECT_ID}"
echo ""
echo "========================================"
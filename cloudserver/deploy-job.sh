#!/bin/bash

PROJECT_ID="project-717dce1d-b530-431a-b19"
PROJECT_NUMBER="275599637949"  # 从SERVICE_ACCOUNT中提取
REGION="asia-northeast1"
REPO="bnbs-repo"
JOB_NAME="trading-signal-job"
SERVICE_ACCOUNT="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/trading-job"

echo "========================================"
echo "  Cloud Run Job 部署"
echo "  JOB NAME: ${JOB_NAME}"
echo "========================================"
echo ""

# 1. 确认Artifact Registry存在（与Web共用）
echo "▶ 1. Artifact Registry 確認（Webと共用）"
gcloud artifacts repositories describe "${REPO}" \
  --location="${REGION}" \
  --project="${PROJECT_ID}" > /dev/null 2>&1 || \
  gcloud artifacts repositories create "${REPO}" \
    --repository-format=docker \
    --location="${REGION}" \
    --project="${PROJECT_ID}"
echo "   完了"
echo ""

# 2. 给服务账号添加Firestore权限（首次部署需要）
echo "▶ 2. Firestore権限を設定"
gcloud projects add-iam-policy-binding ${PROJECT_ID} \
  --member="serviceAccount:${SERVICE_ACCOUNT}" \
  --role="roles/datastore.user" \
  --condition=None 2>&1 | grep -v "ETag" || true
echo "   Firestore権限付与完了"
echo ""

# 3. 构建镜像（使用Dockerfile.job）
echo "▶ 3. Docker ビルド（Dockerfile.job）"
docker build --no-cache -f Dockerfile.job -t ${IMAGE} . \
  || { echo "❌ Docker build failed"; exit 1; }
echo ""

# 4. 推送镜像
echo "▶ 4. イメージを Artifact Registry へ push"
docker push ${IMAGE} \
  || { echo "❌ Docker push failed"; exit 1; }
echo ""

# 5. 删除旧的Job（如果存在）
echo "▶ 5. 既存のJobがあれば削除"
if gcloud beta run jobs describe ${JOB_NAME} --region ${REGION} --project ${PROJECT_ID} &>/dev/null; then
    echo "   Job存在，删除中..."
    echo "y" | gcloud beta run jobs delete ${JOB_NAME} \
      --region ${REGION} \
      --project ${PROJECT_ID} --quiet
    echo "   ✅ 删除完成"
else
    echo "   Job不存在，跳过删除"
fi

# 6. 创建Cloud Run Job
echo "▶ 6. Cloud Run Job を作成"
gcloud beta run jobs create ${JOB_NAME} \
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

# 7. 创建Cloud Scheduler定时任务（每5分钟）
echo "▶ 7. Cloud Scheduler 定时任务を作成"
gcloud scheduler jobs delete trading-signal-schedule \
  --location ${REGION} \
  --project ${PROJECT_ID} \
  --quiet 2>/dev/null || true

gcloud scheduler jobs create pubsub trading-signal-schedule \
  --schedule="*/5 * * * *" \
  --location=${REGION} \
  --topic=run-trading-job \
  --message-body='{"job": "trading-signal-job"}' \
  --project=${PROJECT_ID}
echo ""

# 8. 测试执行
echo "▶ 8. テスト実行"
gcloud beta run jobs execute ${JOB_NAME} \
  --region ${REGION} \
  --project ${PROJECT_ID} \
  --wait
echo ""

echo "========================================"
echo "✅ Job部署完了"
echo "   Job: ${JOB_NAME}"
echo "   定时任务: */5 * * * * (每5分钟)"
echo "========================================"
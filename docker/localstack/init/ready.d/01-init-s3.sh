#!/bin/bash
# LocalStack 기동 완료(ready) 후 1회 실행돼 로컬 개발용 S3 버킷을 만든다.
# awslocal은 LocalStack 이미지에 내장된 AWS CLI 래퍼로, 엔드포인트가 자동으로 LocalStack을 가리킨다.
# (이미 존재하면 무시 — 컨테이너를 재기동해도 안전하다)
set -euo pipefail

BUCKET="${MEEPLE_S3_BUCKET:-meeple-local}"

awslocal s3 mb "s3://${BUCKET}" 2>/dev/null || true
echo "LocalStack S3 버킷 준비 완료: ${BUCKET}"

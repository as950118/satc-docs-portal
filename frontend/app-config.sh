#!/bin/sh
set -eu

: "${VITE_API_BASE_URL:?VITE_API_BASE_URL 이 비어 있습니다. 브라우저가 호출할 백엔드 주소를 설정하세요 (예: https://api.docs.example.com)}"

cat > /usr/share/nginx/html/config.js <<CONFIG
window.__APP_CONFIG__ = {
  VITE_API_BASE_URL: "${VITE_API_BASE_URL}",
  VITE_JENKINS_URL: "${VITE_JENKINS_URL:-}",
  VITE_ARGOCD_URL: "${VITE_ARGOCD_URL:-}",
  VITE_COMMON_CODE_URL: "${VITE_COMMON_CODE_URL:-}"
}
CONFIG

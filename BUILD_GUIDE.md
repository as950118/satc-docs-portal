# Docs Portal 이미지 빌드 가이드

이 문서는 Docs Portal의 각 서버별 Docker 이미지를 빌드하고 tar.gz 파일로 내보내는 방법을 설명합니다.

## 📋 개요

Docs Portal은 다음 3개의 서버로 구성됩니다:
- **Backend**: Spring Boot 애플리케이션 (`docs-portal-back`)
- **Frontend**: Vue.js 애플리케이션 (`docs-portal-front`)  
- **Indexing**: Python FastAPI 애플리케이션 (`docs-portal-indexing`)

## 🚀 빠른 시작

### 전체 이미지 빌드 (권장)
```bash
# 모든 서버 이미지를 latest 태그로 빌드
./build-all-images.sh

# 특정 버전으로 빌드
./build-all-images.sh -v 1.0.0

# 기존 이미지 정리 후 빌드
./build-all-images.sh -v 1.0.0 -c
```

### 개별 서버 빌드
```bash
# Backend만 빌드
./build-all-images.sh --backend-only -v 1.0.0

# Frontend만 빌드  
./build-all-images.sh --frontend-only -v 1.0.0

# Indexing 서버만 빌드
./build-all-images.sh --indexing-only -v 1.0.0
```

## 📂 개별 스크립트 사용법

### Backend 빌드
```bash
cd backend
./build-image.sh -v 1.0.0
```

### Frontend 빌드
```bash
cd frontend
./build-image.sh -v 1.0.0
```

### Indexing 서버 빌드
```bash
cd indexing
./build-image.sh -v 1.0.0

# 오프라인 배포용 빌드
./build-image.sh -v 1.0.0 --offline
```

## 🏷️ 이미지 이름 및 태그

### 이미지 이름
- Backend: `docs-portal-back`
- Frontend: `docs-portal-front`
- Indexing: `docs-portal-indexing`

### 태그 부여 방법
- 기본값: `latest`
- 버전 지정: `-v` 또는 `--version` 옵션 사용
- 예시: `docs-portal-back:1.0.0`, `docs-portal-front:2.1.0`

## 📦 tar.gz 파일 내보내기

모든 빌드 스크립트는 기본적으로 이미지를 tar.gz 파일로 내보냅니다.

### 생성되는 파일명 패턴
- Backend: `docs-portal-back-{TAG}.tar.gz`
- Frontend: `docs-portal-front-{TAG}.tar.gz`
- Indexing: `docs-portal-indexing-{TAG}.tar.gz`
- Indexing (오프라인): `docs-portal-indexing-{TAG}-offline.tar.gz`

### 내보내기 생략
```bash
# tar.gz 파일 생성 생략
./build-all-images.sh --no-export
```

## ⚙️ 고급 옵션

### 통합 빌드 스크립트 옵션
```bash
./build-all-images.sh [옵션]

옵션:
  -v, --version VERSION  모든 이미지의 버전 태그 설정 (기본값: latest)
  -c, --clean           빌드 전 기존 이미지 정리
  --platform PLATFORM   플랫폼 지정 (기본값: linux/arm64)
  --backend-only        백엔드 이미지만 빌드
  --frontend-only       프론트엔드 이미지만 빌드
  --indexing-only       인덱싱 서버 이미지만 빌드
  --offline             인덱싱 서버를 오프라인 모드로 빌드
  --no-export           tar.gz 파일로 내보내기 생략
  --parallel            병렬 빌드 실행 (실험적 기능)
  -h, --help            도움말 표시
```

### 개별 스크립트 공통 옵션
```bash
./build-image.sh [옵션]

옵션:
  -v, --version VERSION  이미지 버전 태그 설정 (기본값: latest)
  -c, --clean           빌드 전 기존 이미지 정리
  --platform PLATFORM   플랫폼 지정 (기본값: linux/amd64)
  --no-export           tar.gz 파일로 내보내기 생략
  -h, --help            도움말 표시
```

### Indexing 서버 추가 옵션
```bash
cd indexing
./build-image.sh --offline    # 오프라인 배포용 이미지 빌드
```

## 🚢 배포 방법

### 1. 서버로 파일 전송
```bash
scp docs-portal-back-1.0.0.tar.gz user@server:/path/to/destination/
scp docs-portal-front-1.0.0.tar.gz user@server:/path/to/destination/
scp docs-portal-indexing-1.0.0.tar.gz user@server:/path/to/destination/
```

### 2. 서버에서 이미지 로드
```bash
docker load < docs-portal-back-1.0.0.tar.gz
docker load < docs-portal-front-1.0.0.tar.gz
docker load < docs-portal-indexing-1.0.0.tar.gz
```

### 3. 컨테이너 실행
```bash
# Backend
docker run -d -p 8080:8080 --name docs-backend docs-portal-back:1.0.0

# Frontend  
docker run -d -p 80:80 --name docs-frontend docs-portal-front:1.0.0

# Indexing
docker run -d -p 8000:8000 --name docs-indexing docs-portal-indexing:1.0.0
```

### 4. Docker Compose 사용 (권장)
```bash
# 전체 시스템 실행
docker-compose up -d
```

## 🔧 문제 해결

### 빌드 실패 시
1. Docker가 실행 중인지 확인
2. 충분한 디스크 공간이 있는지 확인
3. 네트워크 연결 상태 확인
4. 기존 이미지 정리 후 재시도: `-c` 옵션 사용

### 권한 오류 시
```bash
chmod +x build-all-images.sh
chmod +x backend/build-image.sh
chmod +x frontend/build-image.sh
chmod +x indexing/build-image.sh
```

### 로그 확인
```bash
# 컨테이너 로그 확인
docker logs docs-backend
docker logs docs-frontend
docker logs docs-indexing
```

## 📊 성능 최적화

### 병렬 빌드 (실험적)
```bash
# 모든 이미지를 병렬로 빌드 (빠른 빌드)
./build-all-images.sh --parallel -v 1.0.0
```

### 플랫폼 지정
```bash
# ARM64 플랫폼용 빌드
./build-all-images.sh --platform linux/arm64 -v 1.0.0

# 멀티 플랫폼 빌드 (Docker Buildx 필요)
docker buildx build --platform linux/amd64,linux/arm64 -t docs-portal-back:1.0.0 .
```

## 📝 참고사항

- 모든 스크립트는 `linux/arm64` 플랫폼을 기본으로 합니다
- tar.gz 파일은 gzip으로 압축되어 용량이 최적화됩니다
- 오프라인 배포용 이미지는 모든 의존성을 포함하여 크기가 더 클 수 있습니다
- 병렬 빌드는 시스템 리소스를 많이 사용할 수 있으니 주의하세요

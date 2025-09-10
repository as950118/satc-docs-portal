# 사내 문서 관리 포탈

Vue 3 기반의 사내 문서 관리 시스템입니다. 문서를 트리 구조로 관리하고, 추가/삭제/편집 기능을 제공합니다.

## 주요 기능

### 📁 트리 구조 문서 관리
- 폴더와 문서를 계층적으로 관리
- 드래그 앤 드롭으로 직관적인 구조 변경 (추후 구현 예정)

### 📝 문서 CRUD 기능
- **문서 추가**: 새로운 문서나 폴더 생성
- **문서 조회**: 클릭으로 문서 내용 확인
- **문서 편집**: 마크다운 문법 지원하는 인라인 에디터
- **문서 삭제**: 안전한 삭제 확인 프로세스

### 🎨 현대적인 UI/UX
- Element Plus 기반의 깔끔한 디자인
- 반응형 레이아웃으로 다양한 화면 크기 지원
- 직관적인 아이콘과 색상 체계

## 기술 스택

- **Vue 3**: Composition API 사용
- **Vue Router**: 라우팅 관리
- **Element Plus**: UI 컴포넌트 라이브러리
- **Vite**: 빠른 개발 환경

## 설치 및 실행

### 로컬 개발 환경

#### 1. 의존성 설치
```bash
npm install
```

#### 2. 개발 서버 실행
```bash
npm run dev
```

브라우저에서 `http://localhost:3000`으로 접속하여 확인할 수 있습니다.

#### 3. 프로덕션 빌드
```bash
npm run build
```

### Docker를 사용한 실행

#### 1. 프로덕션 환경 (권장)
```bash
# 이미지 빌드
docker build -t docs-front .

# 컨테이너 실행
docker run -p 80:80 docs-front
```

브라우저에서 `http://localhost`로 접속하여 확인할 수 있습니다.

#### 2. 개발 환경
```bash
# 개발용 이미지 빌드
docker build -f Dockerfile.dev -t docs-front:dev .

# 개발 컨테이너 실행 (볼륨 마운트로 실시간 반영)
docker run -p 3000:3000 -v $(pwd):/app -v /app/node_modules docs-front:dev
```

#### 3. Docker Compose 사용
```bash
# 프로덕션 환경
docker-compose up -d

# 개발 환경
docker-compose --profile dev up -d docs-front-dev
```

#### 4. 빌드 스크립트 사용 (권장)
```bash
# 기본 프로덕션 빌드
./build.sh

# 커스텀 이름과 태그로 빌드
./build.sh -n myapp -t v1.0.0

# 개발 환경 빌드
./build.sh -d

# 레지스트리에 푸시
./build.sh -r registry.example.com -p

# 도움말 보기
./build.sh -h
```

## 프로젝트 구조

```
src/
├── components/          # 재사용 가능한 컴포넌트
│   ├── DocumentTree.vue    # 문서 트리 컴포넌트
│   └── DocumentViewer.vue  # 문서 뷰어 컴포넌트
├── views/              # 페이지 컴포넌트
│   ├── Home.vue           # 메인 홈 페이지
│   └── DocumentView.vue   # 단독 문서 조회 페이지
├── App.vue             # 루트 컴포넌트
└── main.js             # 애플리케이션 진입점
```

## 사용법

### 문서 추가
1. 좌측 트리에서 "새 문서 추가" 버튼 클릭
2. 문서 제목, 타입(문서/폴더), 상위폴더 선택
3. "추가" 버튼으로 생성 완료

### 문서 편집
1. 트리에서 문서 클릭하여 선택
2. 우측 뷰어에서 "편집" 버튼 클릭
3. 마크다운 문법으로 내용 작성
4. "저장" 버튼으로 변경사항 저장

### 문서 삭제
1. 트리에서 문서 호버 시 나타나는 삭제 버튼 클릭
2. 확인 다이얼로그에서 "삭제" 확인

## 마크다운 지원

문서 편집 시 다음 마크다운 문법을 사용할 수 있습니다:

- `# 제목1`, `## 제목2`, `### 제목3`
- `**굵게**`, `*기울임*`
- `- 목록 항목`
- `[링크 텍스트](URL)`

## 향후 개발 계획

- [ ] 드래그 앤 드롭으로 문서 이동
- [ ] 문서 검색 기능
- [ ] 문서 태그 시스템
- [ ] 문서 버전 관리
- [ ] 사용자 권한 관리
- [ ] 문서 공유 기능
- [ ] 마크다운 미리보기 개선
- [ ] 백엔드 API 연동

## Docker 이미지 최적화

### 멀티스테이지 빌드
- **빌드 스테이지**: Node.js 18 Alpine 이미지로 의존성 설치 및 빌드
- **프로덕션 스테이지**: Nginx Alpine 이미지로 정적 파일 서빙
- **최종 이미지 크기**: 약 25MB (압축 시 약 10MB)

### 보안 및 성능 최적화
- Nginx 보안 헤더 설정
- Gzip 압축 활성화
- 정적 파일 캐싱 설정
- 헬스체크 포함
- 비루트 사용자로 실행

## 환경 변수 설정

Docker 컨테이너 실행 시 다음 환경 변수를 설정할 수 있습니다:

```bash
docker run -p 80:80 \
  -e VITE_API_BASE_URL=http://your-api-server:8080/api \
  -e VITE_MINIO_ENDPOINT=your-minio-server \
  -e VITE_MINIO_PORT=9000 \
  -e VITE_MINIO_BUCKET_NAME=cmp-docs \
  docs-front
```

### 주요 환경 변수
- `VITE_API_BASE_URL`: 백엔드 API 서버 URL
- `VITE_MINIO_ENDPOINT`: MinIO 서버 엔드포인트
- `VITE_MINIO_PORT`: MinIO 서버 포트
- `VITE_MINIO_USE_SSL`: SSL 사용 여부 (true/false)
- `VITE_MINIO_BUCKET_NAME`: MinIO 버킷 이름

## 배포 가이드

### 1. 로컬 빌드 및 테스트
```bash
# 이미지 빌드
./build.sh -n docs-front -t v1.0.0

# 로컬 테스트
docker run -p 8080:80 docs-front:v1.0.0
```

### 2. 컨테이너 레지스트리에 푸시
```bash
# Docker Hub
./build.sh -n your-username/docs-front -t v1.0.0 -p

# 프라이빗 레지스트리
./build.sh -r registry.your-company.com -n docs-front -t v1.0.0 -p
```

### 3. 프로덕션 배포
```bash
# Kubernetes
kubectl apply -f k8s/

# Docker Swarm
docker stack deploy -c docker-compose.prod.yml docs-front

# 단일 서버
docker run -d --name docs-front \
  --restart unless-stopped \
  -p 80:80 \
  your-registry/docs-front:v1.0.0
```

## 개발 참고사항

현재는 프론트엔드만 구현되어 있으며, 문서 데이터는 컴포넌트 내 로컬 상태로 관리됩니다. 
실제 서비스에서는 다음과 같은 백엔드 API 연동이 필요합니다:

- `GET /api/documents` - 문서 트리 조회
- `POST /api/documents` - 문서 생성
- `PUT /api/documents/:id` - 문서 수정
- `DELETE /api/documents/:id` - 문서 삭제

### API 프록시 설정
nginx.conf 파일에서 백엔드 API 서버로의 프록시 설정을 수정하여 사용하세요:

```nginx
location /api/ {
    proxy_pass http://your-backend-server:8080/api/;
    # ... 기타 프록시 설정
}
```


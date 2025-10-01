#!/bin/bash

# Indexing Server Docker 이미지 빌드 및 tar.gz 내보내기 스크립트
# docs-portal-indexing 이미지 생성

set -e  # 에러 발생 시 스크립트 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 기본 설정
IMAGE_NAME="docs-portal-indexing"
DEFAULT_TAG="latest"
PLATFORM="linux/arm64"

# 함수 정의
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 도움말 함수
show_help() {
    echo "사용법: $0 [옵션]"
    echo ""
    echo "옵션:"
    echo "  -v, --version VERSION  이미지 버전 태그 설정 (기본값: latest)"
    echo "  -c, --clean           빌드 전 기존 이미지 정리"
    echo "  --platform PLATFORM   플랫폼 지정 (기본값: linux/arm64)"
    echo "                        지원 플랫폼: linux/arm64, linux/amd64"
    echo "  --offline             오프라인 배포용 이미지 빌드 (Dockerfile.offline 사용)"
    echo "  --no-export           tar.gz 파일로 내보내기 생략"
    echo "  -h, --help            도움말 표시"
    echo ""
    echo "예시:"
    echo "  $0                           # latest 태그로 ARM64 빌드 및 내보내기"
    echo "  $0 -v 1.0.0                 # v1.0.0 태그로 빌드 및 내보내기"
    echo "  $0 -v 1.2.3 -c              # v1.2.3 태그로 빌드 (기존 이미지 정리)"
    echo "  $0 --platform linux/amd64   # AMD64 플랫폼으로 빌드"
    echo "  $0 --offline                # 오프라인 배포용 이미지 빌드"
    echo "  $0 --no-export              # 빌드만 하고 tar.gz 내보내기 생략"
}

# 인자 파싱
TAG="$DEFAULT_TAG"
CLEAN=false
EXPORT=true
OFFLINE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--version)
            TAG="$2"
            shift 2
            ;;
        -c|--clean)
            CLEAN=true
            shift
            ;;
        --platform)
            PLATFORM="$2"
            # 플랫폼 유효성 검사
            if [[ "$PLATFORM" != "linux/arm64" && "$PLATFORM" != "linux/amd64" ]]; then
                print_error "지원하지 않는 플랫폼: $PLATFORM"
                print_info "지원 플랫폼: linux/arm64, linux/amd64"
                exit 1
            fi
            shift 2
            ;;
        --offline)
            OFFLINE=true
            shift
            ;;
        --no-export)
            EXPORT=false
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            print_error "알 수 없는 옵션: $1"
            show_help
            exit 1
            ;;
    esac
done

# 전체 이미지 이름 구성
FULL_IMAGE_NAME="${IMAGE_NAME}:${TAG}"

print_info "🚀 Indexing Server Docker 이미지 빌드 시작"
print_info "이미지 이름: $FULL_IMAGE_NAME"
print_info "플랫폼: $PLATFORM"

if [ "$OFFLINE" = true ]; then
    print_info "오프라인 배포용 이미지 빌드 모드"
fi

# 기존 이미지 정리
if [ "$CLEAN" = true ]; then
    print_info "기존 이미지 정리 중..."
    docker rmi "$FULL_IMAGE_NAME" 2>/dev/null || print_warning "정리할 이미지가 없습니다."
fi

# 오프라인 의존성 디렉토리 생성
if [ "$OFFLINE" = true ]; then
    mkdir -p ./offline-deps
fi

# 플랫폼 호환성 검증
print_info "플랫폼 호환성 검증 중: $PLATFORM"
if ! docker buildx inspect default >/dev/null 2>&1; then
    print_warning "buildx가 설정되지 않았습니다. 기본 빌더를 사용합니다."
fi

# Docker 빌드
if [ "$OFFLINE" = true ]; then
    print_info "오프라인 배포용 Python 애플리케이션 이미지 빌드 중..."
    print_info "주의: ARM64에서 일부 Python 패키지의 wheel 빌드에 시간이 걸릴 수 있습니다."
    docker build --platform "$PLATFORM" -f Dockerfile.offline -t "$FULL_IMAGE_NAME" .
else
    print_info "Python 애플리케이션 이미지 빌드 중..."
    if [ "$PLATFORM" = "linux/arm64" ]; then
        print_info "ARM64 플랫폼에서 빌드 중... 일부 패키지는 소스에서 컴파일될 수 있습니다."
    fi
    docker build --platform "$PLATFORM" -t "$FULL_IMAGE_NAME" .
fi

if [ $? -eq 0 ]; then
    print_success "이미지 빌드 완료: $FULL_IMAGE_NAME"
else
    print_error "이미지 빌드 실패"
    exit 1
fi

# 이미지 정보 출력
print_info "이미지 정보:"
docker images "$FULL_IMAGE_NAME"

# 이미지를 tar.gz 파일로 내보내기
if [ "$EXPORT" = true ]; then
    if [ "$OFFLINE" = true ]; then
        EXPORT_FILE="${IMAGE_NAME}-${TAG}-offline.tar.gz"
    else
        EXPORT_FILE="${IMAGE_NAME}-${TAG}.tar.gz"
    fi
    
    print_info "이미지를 tar.gz 파일로 내보내는 중: $EXPORT_FILE"
    
    # Docker save로 tar 파일 생성 후 gzip으로 압축
    docker save "$FULL_IMAGE_NAME" | gzip > "$EXPORT_FILE"
    
    if [ $? -eq 0 ]; then
        print_success "이미지 내보내기 완료: $EXPORT_FILE"
        
        # 파일 크기 정보 출력
        if command -v ls >/dev/null 2>&1; then
            FILE_SIZE=$(ls -lh "$EXPORT_FILE" | awk '{print $5}')
            print_info "압축된 파일 크기: $FILE_SIZE"
        fi
        
        echo ""
        print_info "📦 배포 방법:"
        echo "  1. 서버로 파일 전송: scp $EXPORT_FILE user@server:/path/to/destination/"
        echo "  2. 서버에서 이미지 로드: docker load < $EXPORT_FILE"
        echo "  3. 컨테이너 실행: docker run -d -p 8000:8000 --name docs-indexing $FULL_IMAGE_NAME"
    else
        print_error "이미지 내보내기 실패"
        exit 1
    fi
fi

print_success "✅ 모든 작업이 완료되었습니다!"

# 실행 예시 출력
echo ""
print_info "🔧 컨테이너 실행 예시:"
echo "  docker run -d -p 8000:8000 --name docs-indexing $FULL_IMAGE_NAME"
echo ""
print_info "🔍 컨테이너 로그 확인:"
echo "  docker logs docs-indexing"

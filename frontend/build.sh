#!/bin/bash

# 컨테이너 이미지 빌드 스크립트

set -e  # 에러 발생 시 스크립트 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 기본 설정
IMAGE_NAME="docs-front"
TAG="latest"
REGISTRY=""
PLATFORM="linux/amd64"

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
    echo "  -n, --name NAME       이미지 이름 설정 (기본값: docs-front)"
    echo "  -t, --tag TAG         이미지 태그 설정 (기본값: latest)"
    echo "  -r, --registry REG    레지스트리 URL 설정"
    echo "  -p, --push            빌드 후 레지스트리에 푸시"
    echo "  -d, --dev             개발 환경 이미지 빌드"
    echo "  -c, --clean           빌드 전 기존 이미지 정리"
    echo "  --platform PLATFORM   플랫폼 지정 (기본값: linux/amd64)"
    echo "  -e, --export          이미지를 tar.gz 파일로 내보내기"
    echo "  -h, --help            도움말 표시"
    echo ""
    echo "예시:"
    echo "  $0                              # 기본 프로덕션 이미지 빌드"
    echo "  $0 -n myapp -t v1.0.0          # 커스텀 이름과 태그로 빌드"
    echo "  $0 -r registry.example.com -p  # 레지스트리에 푸시"
    echo "  $0 -d                          # 개발 환경 이미지 빌드"
    echo "  $0 -e                          # 이미지를 tar.gz 파일로 내보내기"
    echo "  $0 --platform linux/arm64 -e  # ARM64용 이미지 빌드 및 내보내기"
}

# 인자 파싱
PUSH=false
DEV=false
CLEAN=false
EXPORT=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -n|--name)
            IMAGE_NAME="$2"
            shift 2
            ;;
        -t|--tag)
            TAG="$2"
            shift 2
            ;;
        -r|--registry)
            REGISTRY="$2"
            shift 2
            ;;
        -p|--push)
            PUSH=true
            shift
            ;;
        -d|--dev)
            DEV=true
            shift
            ;;
        -c|--clean)
            CLEAN=true
            shift
            ;;
        --platform)
            PLATFORM="$2"
            shift 2
            ;;
        -e|--export)
            EXPORT=true
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
if [ -n "$REGISTRY" ]; then
    FULL_IMAGE_NAME="${REGISTRY}/${IMAGE_NAME}:${TAG}"
else
    FULL_IMAGE_NAME="${IMAGE_NAME}:${TAG}"
fi

print_info "컨테이너 이미지 빌드 시작"
print_info "이미지 이름: $FULL_IMAGE_NAME"
print_info "플랫폼: $PLATFORM"

# 기존 이미지 정리
if [ "$CLEAN" = true ]; then
    print_info "기존 이미지 정리 중..."
    docker rmi "$FULL_IMAGE_NAME" 2>/dev/null || print_warning "정리할 이미지가 없습니다."
fi

# Docker 빌드
if [ "$DEV" = true ]; then
    print_info "개발 환경 이미지 빌드 중..."
    docker build --platform "$PLATFORM" -f Dockerfile.dev -t "$FULL_IMAGE_NAME" .
else
    print_info "프로덕션 이미지 빌드 중..."
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

# 레지스트리에 푸시
if [ "$PUSH" = true ]; then
    if [ -z "$REGISTRY" ]; then
        print_error "푸시하려면 레지스트리 URL이 필요합니다. (-r 옵션 사용)"
        exit 1
    fi
    
    print_info "레지스트리에 푸시 중: $FULL_IMAGE_NAME"
    docker push "$FULL_IMAGE_NAME"
    
    if [ $? -eq 0 ]; then
        print_success "푸시 완료: $FULL_IMAGE_NAME"
    else
        print_error "푸시 실패"
        exit 1
    fi
fi

# 이미지를 tar.gz 파일로 내보내기
if [ "$EXPORT" = true ]; then
    EXPORT_FILE="${IMAGE_NAME}-${TAG}.tar.gz"
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
        
        print_info "이미지 로드 방법: docker load < $EXPORT_FILE"
    else
        print_error "이미지 내보내기 실패"
        exit 1
    fi
fi

print_success "모든 작업이 완료되었습니다!"

# 실행 예시 출력
echo ""
print_info "컨테이너 실행 예시:"
if [ "$DEV" = true ]; then
    echo "  docker run -p 3000:3000 -v \$(pwd):/app -v /app/node_modules $FULL_IMAGE_NAME"
else
    echo "  docker run -p 80:80 $FULL_IMAGE_NAME"
fi

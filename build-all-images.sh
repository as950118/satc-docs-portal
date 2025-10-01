#!/bin/bash

# 전체 Docs Portal 이미지 빌드 및 tar.gz 내보내기 통합 스크립트
# backend, frontend, indexing 서버 이미지를 한번에 빌드

set -e  # 에러 발생 시 스크립트 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 기본 설정
DEFAULT_TAG="latest"
PLATFORM="linux/arm64"

# 이미지 이름 정의
BACKEND_IMAGE="docs-portal-back"
FRONTEND_IMAGE="docs-portal-front"
INDEXING_IMAGE="docs-portal-indexing"

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

print_header() {
    echo -e "${PURPLE}========================================${NC}"
    echo -e "${PURPLE}$1${NC}"
    echo -e "${PURPLE}========================================${NC}"
}

print_step() {
    echo -e "${CYAN}[STEP]${NC} $1"
}

# 도움말 함수
show_help() {
    echo "사용법: $0 [옵션]"
    echo ""
    echo "옵션:"
    echo "  -v, --version VERSION  모든 이미지의 버전 태그 설정 (기본값: latest)"
    echo "  -c, --clean           빌드 전 기존 이미지 정리"
    echo "  --platform PLATFORM   플랫폼 지정 (기본값: linux/arm64)"
    echo "  --backend-only        백엔드 이미지만 빌드"
    echo "  --frontend-only       프론트엔드 이미지만 빌드"
    echo "  --indexing-only       인덱싱 서버 이미지만 빌드"
    echo "  --offline             인덱싱 서버를 오프라인 모드로 빌드"
    echo "  --no-export           tar.gz 파일로 내보내기 생략"
    echo "  --parallel            병렬 빌드 실행 (실험적 기능)"
    echo "  -h, --help            도움말 표시"
    echo ""
    echo "예시:"
    echo "  $0                     # 모든 이미지를 latest 태그로 빌드"
    echo "  $0 -v 1.0.0           # 모든 이미지를 v1.0.0 태그로 빌드"
    echo "  $0 -v 1.2.3 -c        # v1.2.3 태그로 빌드 (기존 이미지 정리)"
    echo "  $0 --backend-only     # 백엔드 이미지만 빌드"
    echo "  $0 --offline          # 인덱싱 서버를 오프라인 모드로 빌드"
    echo "  $0 --parallel         # 병렬로 모든 이미지 빌드"
}

# 빌드 함수들
build_backend() {
    local tag=$1
    local clean_flag=$2
    local export_flag=$3
    
    print_step "Backend 이미지 빌드 시작..."
    cd backend
    
    local cmd="./build-image.sh -v $tag --platform $PLATFORM"
    if [ "$clean_flag" = true ]; then
        cmd="$cmd -c"
    fi
    if [ "$export_flag" = false ]; then
        cmd="$cmd --no-export"
    fi
    
    eval $cmd
    cd ..
    print_success "Backend 이미지 빌드 완료"
}

build_frontend() {
    local tag=$1
    local clean_flag=$2
    local export_flag=$3
    
    print_step "Frontend 이미지 빌드 시작..."
    cd frontend
    
    local cmd="./build-image.sh -v $tag --platform $PLATFORM"
    if [ "$clean_flag" = true ]; then
        cmd="$cmd -c"
    fi
    if [ "$export_flag" = false ]; then
        cmd="$cmd --no-export"
    fi
    
    eval $cmd
    cd ..
    print_success "Frontend 이미지 빌드 완료"
}

build_indexing() {
    local tag=$1
    local clean_flag=$2
    local export_flag=$3
    local offline_flag=$4
    
    print_step "Indexing 서버 이미지 빌드 시작..."
    cd indexing
    
    local cmd="./build-image.sh -v $tag --platform $PLATFORM"
    if [ "$clean_flag" = true ]; then
        cmd="$cmd -c"
    fi
    if [ "$export_flag" = false ]; then
        cmd="$cmd --no-export"
    fi
    if [ "$offline_flag" = true ]; then
        cmd="$cmd --offline"
    fi
    
    eval $cmd
    cd ..
    print_success "Indexing 서버 이미지 빌드 완료"
}

# 병렬 빌드 함수
build_parallel() {
    local tag=$1
    local clean_flag=$2
    local export_flag=$3
    local offline_flag=$4
    
    print_info "병렬 빌드 모드로 실행합니다..."
    
    # 백그라운드에서 각 빌드 실행
    build_backend $tag $clean_flag $export_flag &
    BACKEND_PID=$!
    
    build_frontend $tag $clean_flag $export_flag &
    FRONTEND_PID=$!
    
    build_indexing $tag $clean_flag $export_flag $offline_flag &
    INDEXING_PID=$!
    
    # 모든 빌드 완료 대기
    print_info "모든 빌드 프로세스 완료 대기 중..."
    
    wait $BACKEND_PID
    BACKEND_EXIT=$?
    
    wait $FRONTEND_PID
    FRONTEND_EXIT=$?
    
    wait $INDEXING_PID
    INDEXING_EXIT=$?
    
    # 결과 확인
    if [ $BACKEND_EXIT -ne 0 ] || [ $FRONTEND_EXIT -ne 0 ] || [ $INDEXING_EXIT -ne 0 ]; then
        print_error "일부 빌드가 실패했습니다."
        exit 1
    fi
}

# 인자 파싱
TAG="$DEFAULT_TAG"
CLEAN=false
EXPORT=true
BACKEND_ONLY=false
FRONTEND_ONLY=false
INDEXING_ONLY=false
OFFLINE=false
PARALLEL=false

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
            shift 2
            ;;
        --backend-only)
            BACKEND_ONLY=true
            shift
            ;;
        --frontend-only)
            FRONTEND_ONLY=true
            shift
            ;;
        --indexing-only)
            INDEXING_ONLY=true
            shift
            ;;
        --offline)
            OFFLINE=true
            shift
            ;;
        --no-export)
            EXPORT=false
            shift
            ;;
        --parallel)
            PARALLEL=true
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

# 시작 시간 기록
START_TIME=$(date +%s)

print_header "Docs Portal 이미지 빌드 시작"
print_info "버전 태그: $TAG"
print_info "플랫폼: $PLATFORM"
print_info "정리 모드: $CLEAN"
print_info "내보내기: $EXPORT"

if [ "$OFFLINE" = true ]; then
    print_info "인덱싱 서버 오프라인 모드: 활성화"
fi

if [ "$PARALLEL" = true ]; then
    print_info "병렬 빌드 모드: 활성화"
fi

echo ""

# 빌드 실행
if [ "$PARALLEL" = true ] && [ "$BACKEND_ONLY" = false ] && [ "$FRONTEND_ONLY" = false ] && [ "$INDEXING_ONLY" = false ]; then
    # 병렬 빌드 (전체 빌드일 때만)
    build_parallel $TAG $CLEAN $EXPORT $OFFLINE
else
    # 순차 빌드
    if [ "$BACKEND_ONLY" = true ]; then
        build_backend $TAG $CLEAN $EXPORT
    elif [ "$FRONTEND_ONLY" = true ]; then
        build_frontend $TAG $CLEAN $EXPORT
    elif [ "$INDEXING_ONLY" = true ]; then
        build_indexing $TAG $CLEAN $EXPORT $OFFLINE
    else
        # 전체 빌드
        build_backend $TAG $CLEAN $EXPORT
        echo ""
        build_frontend $TAG $CLEAN $EXPORT
        echo ""
        build_indexing $TAG $CLEAN $EXPORT $OFFLINE
    fi
fi

# 완료 시간 계산
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))
MINUTES=$((DURATION / 60))
SECONDS=$((DURATION % 60))

echo ""
print_header "빌드 완료 요약"

# 빌드된 이미지 목록 출력
print_info "빌드된 이미지 목록:"
if [ "$BACKEND_ONLY" = false ] && [ "$FRONTEND_ONLY" = false ] && [ "$INDEXING_ONLY" = false ]; then
    # 전체 빌드
    docker images | grep -E "(docs-portal-back|docs-portal-front|docs-portal-indexing)" | grep "$TAG"
elif [ "$BACKEND_ONLY" = true ]; then
    docker images | grep "docs-portal-back" | grep "$TAG"
elif [ "$FRONTEND_ONLY" = true ]; then
    docker images | grep "docs-portal-front" | grep "$TAG"
elif [ "$INDEXING_ONLY" = true ]; then
    docker images | grep "docs-portal-indexing" | grep "$TAG"
fi

echo ""
print_success "✅ 모든 빌드가 성공적으로 완료되었습니다!"
print_info "총 소요 시간: ${MINUTES}분 ${SECONDS}초"

if [ "$EXPORT" = true ]; then
    echo ""
    print_info "📦 생성된 tar.gz 파일들:"
    find . -name "*.tar.gz" -newer /tmp/build_start_marker 2>/dev/null || find . -name "*.tar.gz" -mmin -$((DURATION/60+1)) 2>/dev/null || ls -la *.tar.gz 2>/dev/null || echo "  (tar.gz 파일을 찾을 수 없습니다)"
fi

echo ""
print_info "🚀 Docker Compose로 전체 시스템 실행:"
echo "  docker-compose up -d"

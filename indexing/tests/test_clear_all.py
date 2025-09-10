#!/usr/bin/env python3
"""
전체 삭제 API 테스트 스크립트
"""
import requests
import json

# 서버 URL
BASE_URL = "http://localhost:8000/api/v1"

def test_clear_all():
    """ES의 모든 청크와 문서 삭제 테스트"""
    print("=== 전체 데이터 삭제 테스트 ===")
    print("⚠️  주의: 이 작업은 ES의 모든 청크와 문서를 삭제합니다!")
    
    # 사용자 확인
    confirm = input("정말로 모든 데이터를 삭제하시겠습니까? (yes/no): ")
    if confirm.lower() != 'yes':
        print("작업이 취소되었습니다.")
        return
    
    try:
        response = requests.delete(f"{BASE_URL}/clear-all")
        
        print(f"Status Code: {response.status_code}")
        
        if response.status_code == 200:
            result = response.json()
            print(f"전체 작업 성공: {result['success']}")
            print(f"삭제된 청크 수: {result['deleted_chunks']}")
            print(f"삭제된 문서 수: {result['deleted_documents']}")
            print(f"청크 인덱스: {result['chunks_index']}")
            print(f"문서 인덱스: {result['documents_index']}")
            print(f"메시지: {result['message']}")
        else:
            print(f"Error Response: {response.json()}")
        
    except requests.exceptions.ConnectionError:
        print("서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요.")
    except Exception as e:
        print(f"오류 발생: {e}")

def check_stats_before_and_after():
    """삭제 전후 통계 확인"""
    print("\n=== 삭제 전후 통계 확인 ===")
    
    try:
        # 삭제 전 통계
        print("삭제 전 통계:")
        response = requests.get(f"{BASE_URL}/stats")
        if response.status_code == 200:
            stats = response.json()
            print(f"  청크 수: {stats.get('elasticsearch', {}).get('document_count', 'N/A')}")
            print(f"  저장 크기: {stats.get('elasticsearch', {}).get('store_size', 'N/A')} bytes")
        
        # 전체 삭제 실행
        print("\n전체 삭제 실행 중...")
        response = requests.delete(f"{BASE_URL}/clear-all")
        
        if response.status_code == 200:
            result = response.json()
            print(f"삭제 완료: {result['deleted_chunks']}개 청크, {result['deleted_documents']}개 문서")
        
        # 삭제 후 통계
        print("\n삭제 후 통계:")
        response = requests.get(f"{BASE_URL}/stats")
        if response.status_code == 200:
            stats = response.json()
            print(f"  청크 수: {stats.get('elasticsearch', {}).get('document_count', 'N/A')}")
            print(f"  저장 크기: {stats.get('elasticsearch', {}).get('store_size', 'N/A')} bytes")
        
    except requests.exceptions.ConnectionError:
        print("서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요.")
    except Exception as e:
        print(f"오류 발생: {e}")

if __name__ == "__main__":
    # 간단한 메뉴
    print("1. 전체 데이터 삭제")
    print("2. 삭제 전후 통계 확인")
    choice = input("선택하세요 (1 또는 2): ")
    
    if choice == "1":
        test_clear_all()
    elif choice == "2":
        check_stats_before_and_after()
    else:
        print("잘못된 선택입니다.")

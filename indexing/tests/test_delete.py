#!/usr/bin/env python3
"""
삭제 API 테스트 스크립트
"""
import requests
import json

# 서버 URL
BASE_URL = "http://localhost:8000/api/v1"

def test_delete_by_document_id():
    """문서 ID로 삭제 테스트"""
    print("=== 문서 ID로 삭제 테스트 ===")
    
    # 삭제 요청 데이터
    delete_data = {
        "documentId": "test-document"
    }
    
    try:
        response = requests.delete(
            f"{BASE_URL}/document",
            json=delete_data,
            headers={"Content-Type": "application/json"}
        )
        
        print(f"Status Code: {response.status_code}")
        print(f"Response: {response.json()}")
        
    except requests.exceptions.ConnectionError:
        print("서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요.")
    except Exception as e:
        print(f"오류 발생: {e}")

def test_delete_by_source_file():
    """소스 파일로 삭제 테스트"""
    print("\n=== 소스 파일로 삭제 테스트 ===")
    
    source_file = "documents/test-document.md"
    
    try:
        response = requests.delete(f"{BASE_URL}/files/{source_file}")
        
        print(f"Status Code: {response.status_code}")
        print(f"Response: {response.json()}")
        
    except requests.exceptions.ConnectionError:
        print("서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요.")
    except Exception as e:
        print(f"오류 발생: {e}")

if __name__ == "__main__":
    test_delete_by_document_id()
    test_delete_by_source_file()

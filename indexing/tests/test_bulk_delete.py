#!/usr/bin/env python3
"""
일괄 삭제 API 테스트 스크립트
"""
import requests
import json

# 서버 URL
BASE_URL = "http://localhost:8000/api/v1"

def test_bulk_delete():
    """일괄 문서 삭제 테스트"""
    print("=== 일괄 문서 삭제 테스트 ===")
    
    # 삭제 요청 데이터
    delete_data = {
        "fileNames": [
            "abc-dwer-123.md",
            "test-document.md",
            "guide-policy.md",
            "nonexistent-file.md"
        ]
    }
    
    try:
        response = requests.delete(
            f"{BASE_URL}/documents",
            json=delete_data,
            headers={"Content-Type": "application/json"}
        )
        
        print(f"Status Code: {response.status_code}")
        
        if response.status_code == 200:
            result = response.json()
            print(f"전체 성공 여부: {result['success']}")
            print(f"총 파일 수: {result['total_files']}")
            print(f"성공한 삭제: {result['successful_deletions']}")
            print(f"실패한 삭제: {result['failed_deletions']}")
            print(f"삭제된 총 청크 수: {result['total_deleted_chunks']}")
            print(f"삭제된 총 문서 수: {result['total_deleted_documents']}")
            print(f"메시지: {result['message']}")
            
            print("\n=== 개별 파일 결과 ===")
            for i, file_result in enumerate(result['results'], 1):
                print(f"{i}. {file_result['file_name']}")
                print(f"   성공: {file_result['success']}")
                print(f"   삭제된 청크: {file_result['deleted_chunks']}")
                print(f"   전체 문서 삭제: {file_result['deleted_full_document']}")
                print(f"   메시지: {file_result['message']}")
                print()
        else:
            print(f"Error Response: {response.json()}")
        
    except requests.exceptions.ConnectionError:
        print("서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요.")
    except Exception as e:
        print(f"오류 발생: {e}")

def test_empty_list():
    """빈 리스트 테스트"""
    print("\n=== 빈 파일 리스트 테스트 ===")
    
    delete_data = {
        "fileNames": []
    }
    
    try:
        response = requests.delete(
            f"{BASE_URL}/documents",
            json=delete_data,
            headers={"Content-Type": "application/json"}
        )
        
        print(f"Status Code: {response.status_code}")
        print(f"Response: {response.json()}")
        
    except requests.exceptions.ConnectionError:
        print("서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요.")
    except Exception as e:
        print(f"오류 발생: {e}")

def test_single_file():
    """단일 파일 테스트"""
    print("\n=== 단일 파일 삭제 테스트 ===")
    
    delete_data = {
        "fileNames": ["single-test.md"]
    }
    
    try:
        response = requests.delete(
            f"{BASE_URL}/documents",
            json=delete_data,
            headers={"Content-Type": "application/json"}
        )
        
        print(f"Status Code: {response.status_code}")
        print(f"Response: {response.json()}")
        
    except requests.exceptions.ConnectionError:
        print("서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요.")
    except Exception as e:
        print(f"오류 발생: {e}")

if __name__ == "__main__":
    test_bulk_delete()
    test_empty_list()
    test_single_file()

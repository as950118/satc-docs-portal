// MinIO 설정 (브라우저 환경에서는 직접 HTTP 요청 사용)
const MINIO_CONFIG = {
  endPoint: 'localhost', // LOCAL (docker-compose)
  // endPoint: '15.164.158.85', // AWS
  // endPoint: '172.16.0.40',  // 사내 환경
  port: 9000,
  useSSL: false,
  bucketName: 'docs'
}

// MinIO 직접 URL 생성
const getMinioDirectUrl = (objectName) => {
  const protocol = MINIO_CONFIG.useSSL ? 'https' : 'http'
  const port = MINIO_CONFIG.port ? `:${MINIO_CONFIG.port}` : ''
  return `${protocol}://${MINIO_CONFIG.endPoint}${port}/${MINIO_CONFIG.bucketName}/${objectName}`
}

// MinIO 유틸리티 함수들 (브라우저 친화적)
export const minioUtils = {
  // 버킷 이름
  bucketName: MINIO_CONFIG.bucketName,

  // 파일 다운로드 (브라우저 친화적 방식)
  async downloadFile(objectName) {
    try {
      // 직접 HTTP 요청으로 파일 다운로드
      const url = getMinioDirectUrl(objectName)
      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Accept': '*/*'
        }
      })
      
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }
      
      const content = await response.text()
      console.log('MinIO 파일 다운로드 완료:', content.length, '바이트')
      return content
    } catch (error) {
      console.error('MinIO 파일 다운로드 실패:', error)
      throw error
    }
  },

  // 직접 URL 반환 (공개 버킷용)
  getDirectUrl(objectName) {
    return getMinioDirectUrl(objectName)
  },

  // 설정 정보 반환
  getConfig() {
    return { ...MINIO_CONFIG }
  }
}

// 기본 export
export default minioUtils
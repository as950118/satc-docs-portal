package com.docsportal.docs.format.request;

/**
 * 외부 API 호출을 위한 요청 DTO
 * 문서 업로드 완료 후 호출되는 API의 요청 형식
 */
public class ExternalApiRequest {
    
    private String bucketName;  // MinIO 버킷 이름 (ex. cmp-docs)
    private String objectName;  // MinIO Object 이름 (ex. documents/abcd.md)
    
    public ExternalApiRequest() {
    }
    
    public ExternalApiRequest(String bucketName, String objectName) {
        this.bucketName = bucketName;
        this.objectName = objectName;
    }
    
    public String getBucketName() {
        return bucketName;
    }
    
    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }
    
    public String getObjectName() {
        return objectName;
    }
    
    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }
    
    @Override
    public String toString() {
        return "ExternalApiRequest{" +
                "bucketName='" + bucketName + '\'' +
                ", objectName='" + objectName + '\'' +
                '}';
    }
}

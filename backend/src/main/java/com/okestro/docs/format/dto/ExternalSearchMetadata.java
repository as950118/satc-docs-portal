package com.okestro.docs.format.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 외부 검색 API 응답의 메타데이터 DTO
 */
public class ExternalSearchMetadata {
    
    @JsonProperty("source_file")
    private String sourceFile;
    
    @JsonProperty("chunk_index")
    private Integer chunkIndex;
    
    @JsonProperty("chunk_type")
    private String chunkType;
    
    @JsonProperty("content_length")
    private Integer contentLength;
    
    @JsonProperty("Header 1")
    private String header1;
    
    @JsonProperty("bucket_name")
    private String bucketName;
    
    @JsonProperty("object_name")
    private String objectName;
    
    @JsonProperty("file_size")
    private Long fileSize;
    
    @JsonProperty("last_modified")
    private String lastModified;
    
    @JsonProperty("content_type")
    private String contentType;
    
    public ExternalSearchMetadata() {
    }
    
    public String getSourceFile() {
        return sourceFile;
    }
    
    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }
    
    public Integer getChunkIndex() {
        return chunkIndex;
    }
    
    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }
    
    public String getChunkType() {
        return chunkType;
    }
    
    public void setChunkType(String chunkType) {
        this.chunkType = chunkType;
    }
    
    public Integer getContentLength() {
        return contentLength;
    }
    
    public void setContentLength(Integer contentLength) {
        this.contentLength = contentLength;
    }
    
    public String getHeader1() {
        return header1;
    }
    
    public void setHeader1(String header1) {
        this.header1 = header1;
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
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}

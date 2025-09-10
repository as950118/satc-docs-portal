package com.okestro.docs.format.response;

import com.okestro.docs.persistence.DocumentStatus;

/**
 * 문서/폴더 생성 시 동기적으로 반환되는 응답 DTO
 */
public class DocumentCreateResponse {
    private String documentId;
    private String title;
    private Boolean isFolder;
    private String extension;
    private DocumentStatus status;

    public DocumentCreateResponse() {
    }

    public DocumentCreateResponse(String documentId, String title, Boolean isFolder) {
        this.documentId = documentId;
        this.title = title;
        this.isFolder = isFolder;
    }

    public DocumentCreateResponse(String documentId, String title, Boolean isFolder, String extension) {
        this.documentId = documentId;
        this.title = title;
        this.isFolder = isFolder;
        this.extension = extension;
    }

    public DocumentCreateResponse(String documentId, String title, Boolean isFolder, String extension, DocumentStatus status) {
        this.documentId = documentId;
        this.title = title;
        this.isFolder = isFolder;
        this.extension = extension;
        this.status = status;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getIsFolder() {
        return isFolder;
    }

    public void setIsFolder(Boolean isFolder) {
        this.isFolder = isFolder;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }
}


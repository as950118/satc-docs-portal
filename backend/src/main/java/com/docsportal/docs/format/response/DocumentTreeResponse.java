package com.docsportal.docs.format.response;

import com.docsportal.docs.format.dto.DocumentTreeNode;
import java.util.List;

public class DocumentTreeResponse {
    
    private List<DocumentTreeNode> tree;
    private int totalCount;
    private int folderCount;
    private int fileCount;
    private String rootPath;
    
    public DocumentTreeResponse() {}
    
    public DocumentTreeResponse(List<DocumentTreeNode> tree) {
        this.tree = tree;
        this.totalCount = calculateTotalCount(tree);
        this.folderCount = calculateFolderCount(tree);
        this.fileCount = this.totalCount - this.folderCount;
    }
    
    private int calculateTotalCount(List<DocumentTreeNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        
        int count = nodes.size();
        for (DocumentTreeNode node : nodes) {
            count += calculateTotalCount(node.getChildren());
        }
        return count;
    }
    
    private int calculateFolderCount(List<DocumentTreeNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        for (DocumentTreeNode node : nodes) {
            if (node.getIsFolder()) {
                count++;
            }
            count += calculateFolderCount(node.getChildren());
        }
        return count;
    }
    
    // Getters and Setters
    public List<DocumentTreeNode> getTree() {
        return tree;
    }
    
    public void setTree(List<DocumentTreeNode> tree) {
        this.tree = tree;
        this.totalCount = calculateTotalCount(tree);
        this.folderCount = calculateFolderCount(tree);
        this.fileCount = this.totalCount - this.folderCount;
    }
    
    public int getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
    
    public int getFolderCount() {
        return folderCount;
    }
    
    public void setFolderCount(int folderCount) {
        this.folderCount = folderCount;
    }
    
    public int getFileCount() {
        return fileCount;
    }
    
    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }
    
    public String getRootPath() {
        return rootPath;
    }
    
    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }
}

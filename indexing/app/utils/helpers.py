"""
Utility helper functions
"""
import hashlib
import mimetypes
from typing import Optional, Dict, Any
from pathlib import Path


def get_file_extension(file_path: str) -> str:
    """
    Get file extension from file path
    
    Args:
        file_path: File path
        
    Returns:
        File extension (without dot)
    """
    return Path(file_path).suffix.lstrip('.').lower()


def get_mime_type(file_path: str) -> str:
    """
    Get MIME type for file
    
    Args:
        file_path: File path
        
    Returns:
        MIME type string
    """
    mime_type, _ = mimetypes.guess_type(file_path)
    return mime_type or "application/octet-stream"


def generate_file_hash(content: bytes, algorithm: str = "sha256") -> str:
    """
    Generate hash for file content
    
    Args:
        content: File content as bytes
        algorithm: Hash algorithm to use
        
    Returns:
        Hex digest of the hash
    """
    hash_obj = hashlib.new(algorithm)
    hash_obj.update(content)
    return hash_obj.hexdigest()


def format_file_size(size_bytes: int) -> str:
    """
    Format file size in human readable format
    
    Args:
        size_bytes: Size in bytes
        
    Returns:
        Formatted size string
    """
    if size_bytes == 0:
        return "0 B"
    
    size_names = ["B", "KB", "MB", "GB", "TB"]
    i = 0
    while size_bytes >= 1024 and i < len(size_names) - 1:
        size_bytes /= 1024.0
        i += 1
    
    return f"{size_bytes:.1f} {size_names[i]}"


def sanitize_filename(filename: str) -> str:
    """
    Sanitize filename by removing/replacing invalid characters
    
    Args:
        filename: Original filename
        
    Returns:
        Sanitized filename
    """
    # Remove or replace invalid characters
    invalid_chars = '<>:"/\\|?*'
    for char in invalid_chars:
        filename = filename.replace(char, '_')
    
    # Remove leading/trailing whitespace and dots
    filename = filename.strip('. ')
    
    # Ensure filename is not empty
    if not filename:
        filename = "unnamed_file"
    
    return filename


def extract_text_preview(text: str, max_length: int = 200) -> str:
    """
    Extract a preview of text content
    
    Args:
        text: Full text content
        max_length: Maximum length of preview
        
    Returns:
        Text preview
    """
    if len(text) <= max_length:
        return text
    
    # Try to cut at word boundary
    preview = text[:max_length]
    last_space = preview.rfind(' ')
    
    if last_space > max_length * 0.8:  # If we can cut at a word boundary reasonably close
        preview = preview[:last_space]
    
    return preview + "..."


def validate_file_path(file_path: str) -> bool:
    """
    Validate file path format
    
    Args:
        file_path: File path to validate
        
    Returns:
        True if valid, False otherwise
    """
    if not file_path or not isinstance(file_path, str):
        return False
    
    # Basic validation - no empty path, no path traversal
    if not file_path.strip() or '..' in file_path:
        return False
    
    # Check for valid characters (basic check)
    invalid_chars = ['<', '>', '|', '?', '*']
    for char in invalid_chars:
        if char in file_path:
            return False
    
    return True


def merge_metadata(base_metadata: Optional[Dict[str, Any]], 
                  additional_metadata: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    """
    Merge metadata dictionaries
    
    Args:
        base_metadata: Base metadata dictionary
        additional_metadata: Additional metadata to merge
        
    Returns:
        Merged metadata dictionary
    """
    result = base_metadata.copy() if base_metadata else {}
    
    if additional_metadata:
        result.update(additional_metadata)
    
    return result

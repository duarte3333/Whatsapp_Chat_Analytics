"""
Security validator to prevent code execution and malicious file uploads.
"""
import os
import zipfile
import re
from pathlib import Path
from typing import Optional, Tuple

# Dangerous file extensions that could execute code
DANGEROUS_EXTENSIONS = {
    # Executables
    'exe', 'bat', 'cmd', 'com', 'scr', 'vbs', 'wsf', 'msi', 'msh',
    # Scripts
    'sh', 'bash', 'zsh', 'ps1', 'psm1', 'psd1', 'py', 'pyc', 'pyw', 'pyo',
    'rb', 'pl', 'pm', 'php', 'php3', 'php4', 'php5', 'phtml', 'jsp', 'jspx',
    'asp', 'aspx', 'js', 'jar', 'war', 'ear',
    # System files
    'dll', 'so', 'dylib', 'sys', 'drv', 'ocx', 'cpl',
    # Archives that could contain executables
    'app', 'deb', 'rpm', 'pkg', 'dmg', 'iso',
    # Other dangerous
    'lnk', 'url', 'reg', 'inf', 'ini'
}

# Dangerous filename patterns
DANGEROUS_PATTERNS = ['..', '/', '\\', ':', '*', '?', '"', '<', '>', '|']

# Maximum entries in ZIP to prevent zip bombs
MAX_ZIP_ENTRIES = 1000


def is_valid_filename(filename: str) -> bool:
    """Validate filename for path traversal and dangerous characters."""
    if not filename or not filename.strip():
        return False
    
    # Check for path traversal
    normalized = filename.replace('\\', '/')
    if '../' in normalized or '..\\' in normalized or normalized.startswith('/') or normalized.startswith('\\'):
        return False
    
    # Check for dangerous characters
    for pattern in DANGEROUS_PATTERNS:
        if pattern in filename:
            return False
    
    # Check for dangerous extensions
    filename_lower = filename.lower()
    for ext in DANGEROUS_EXTENSIONS:
        if filename_lower.endswith(f'.{ext}'):
            return False
    
    return True


def sanitize_filename(filename: str) -> str:
    """Sanitize filename by removing dangerous characters."""
    if not filename:
        return ""
    
    # Remove path traversal attempts
    sanitized = filename.replace('..', '').replace('../', '').replace('..\\', '')
    
    # Remove dangerous characters
    for pattern in DANGEROUS_PATTERNS:
        sanitized = sanitized.replace(pattern, '_')
    
    # Get only the basename (no directory paths)
    sanitized = os.path.basename(sanitized)
    
    return sanitized


def validate_zip_contents(zip_path: str) -> Tuple[Optional[str], bool]:
    """
    Validate ZIP file contents for dangerous files.
    Returns: (error_message, has_chat_file)
    """
    try:
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            entry_count = 0
            has_chat_file = False
            
            for entry_info in zip_ref.infolist():
                entry_count += 1
                
                if entry_count > MAX_ZIP_ENTRIES:
                    return ("ZIP file contains too many entries (possible zip bomb)", False)
                
                entry_name = entry_info.filename
                
                # Validate entry name for path traversal
                if not is_valid_filename(entry_name):
                    return (f"ZIP contains invalid or dangerous filename: {entry_name}", False)
                
                # Check for dangerous file types
                entry_name_lower = entry_name.lower()
                for ext in DANGEROUS_EXTENSIONS:
                    if entry_name_lower.endswith(f'.{ext}'):
                        return (f"ZIP contains dangerous file type: {entry_name}", False)
                
                # Check for any .txt file (prefer _chat.txt but accept any .txt)
                if entry_name_lower.endswith('.txt'):
                    has_chat_file = True
            
            if not has_chat_file:
                return ("The ZIP file does not contain a .txt file", False)
            
            return (None, True)
            
    except zipfile.BadZipFile:
        return ("Invalid ZIP file format", False)
    except Exception as e:
        return (f"Error reading ZIP file: {str(e)}", False)


def validate_file_upload(filename: str, file_size: int, max_size: int = 5 * 1024 * 1024) -> Optional[str]:
    """
    Validate uploaded file for security.
    Returns: error message if invalid, None if valid
    """
    if not filename:
        return "Invalid filename"
    
    # Check file size
    if file_size > max_size:
        return "File size exceeds 5MB limit"
    
    if file_size == 0:
        return "File is empty"
    
    # Validate filename
    if not is_valid_filename(filename):
        return "Invalid or dangerous filename"
    
    filename_lower = filename.lower()
    
    # Accept .txt files directly
    if filename_lower.endswith('.txt'):
        return None  # TXT file is valid
    
    # For ZIP files, we'll validate contents separately
    if filename_lower.endswith('.zip'):
        return None  # ZIP validation will be done when extracting
    
    # File type not supported
    return "File must be a ZIP file or a TXT file"


def sanitize_text_input(text: str, max_length: int = 10000) -> str:
    """
    Sanitize text input to prevent injection attacks.
    Limits length and removes control characters.
    """
    if not text:
        return ""
    
    # Limit length
    if len(text) > max_length:
        text = text[:max_length]
    
    # Remove control characters (except newlines and tabs)
    sanitized = ''.join(char for char in text if ord(char) >= 32 or char in '\n\t')
    
    return sanitized


def is_whatsapp_chat_file(file_path: str) -> bool:
    """
    Check if a file is a WhatsApp chat file by examining its content.
    WhatsApp chat files start with lines like: [DD/MM/YYYY, HH:MM:SS] Sender: Message
    """
    try:
        if not os.path.exists(file_path) or not os.path.isfile(file_path):
            return False
        
        # Check file size - chat files should be reasonable size
        file_size = os.path.getsize(file_path)
        if file_size == 0 or file_size > 10 * 1024 * 1024:  # Max 10MB
            return False
        
        # Read first few lines to check format
        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
            lines_checked = 0
            chat_format_lines = 0
            
            for line in f:
                lines_checked += 1
                if lines_checked > 20:  # Check first 20 lines
                    break
                
                stripped = line.strip()
                if not stripped:
                    continue
                
                # WhatsApp chat format: [DD/MM/YYYY, HH:MM:SS] Sender: Message
                if stripped.startswith('[') and ']' in stripped and ':' in stripped:
                    # Check if it looks like a date/time pattern
                    if any(char.isdigit() for char in stripped[:20]):
                        chat_format_lines += 1
            
            # If at least 2 lines match WhatsApp format, it's likely a chat file
            return chat_format_lines >= 2
    
    except Exception:
        return False


def safe_extract_zip(zip_path: str, extract_to: str) -> Tuple[Optional[str], Optional[str]]:
    """
    Safely extract ZIP file, validating all entries.
    Returns: (error_message, chat_file_path)
    Intelligently finds the WhatsApp chat file by checking content, not just filename.
    """
    error_msg, has_chat_file = validate_zip_contents(zip_path)
    if error_msg:
        return (error_msg, None)
    
    if not has_chat_file:
        return ("The ZIP file does not contain a .txt file with the chat", None)
    
    try:
        txt_files = []
        
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            # Extract all files safely
            for entry_info in zip_ref.infolist():
                entry_name = entry_info.filename
                
                # Skip directories
                if entry_name.endswith('/'):
                    continue
                
                # Sanitize entry name to prevent path traversal
                safe_name = sanitize_filename(entry_name)
                if not safe_name:
                    continue
                
                # Build safe extraction path (preserve subdirectory structure)
                # Handle subdirectories safely
                if '/' in entry_name:
                    # Extract subdirectory structure safely
                    parts = entry_name.split('/')
                    safe_parts = [sanitize_filename(part) for part in parts if part]
                    if not all(safe_parts):
                        continue
                    extract_path = os.path.join(extract_to, *safe_parts)
                else:
                    extract_path = os.path.join(extract_to, safe_name)
                
                # Ensure we're extracting within the temp directory
                extract_path = os.path.normpath(extract_path)
                if not extract_path.startswith(os.path.normpath(extract_to)):
                    continue  # Skip entries that try to escape
                
                # Create parent directories if needed
                os.makedirs(os.path.dirname(extract_path), exist_ok=True)
                
                # Extract file
                zip_ref.extract(entry_info, extract_to)
                
                # Collect all .txt files for analysis
                if entry_name.lower().endswith('.txt') and os.path.exists(extract_path):
                    txt_files.append(extract_path)
        
        if not txt_files:
            return ("No .txt files found in ZIP after extraction", None)
        
        # Priority order for finding chat file:
        # 1. Files named *_chat.txt (iOS format)
        # 2. Files that contain WhatsApp chat format (content check)
        # 3. Largest .txt file (often the chat file)
        # 4. First .txt file found
        
        # Priority 1: Look for *_chat.txt files first
        chat_named_files = [f for f in txt_files if '_chat.txt' in os.path.basename(f).lower()]
        if chat_named_files:
            # If multiple, prefer the one with WhatsApp format
            for f in chat_named_files:
                if is_whatsapp_chat_file(f):
                    return (None, f)
            # If none match format, return the first one
            return (None, chat_named_files[0])
        
        # Priority 2: Check content for WhatsApp format
        for txt_file in txt_files:
            if is_whatsapp_chat_file(txt_file):
                return (None, txt_file)
        
        # Priority 3: Return largest .txt file (usually the chat file)
        txt_files_with_size = [(f, os.path.getsize(f)) for f in txt_files]
        txt_files_with_size.sort(key=lambda x: x[1], reverse=True)
        if txt_files_with_size:
            return (None, txt_files_with_size[0][0])
        
        # Priority 4: Fallback to first file
        return (None, txt_files[0])
        
    except Exception as e:
        return (f"Error extracting ZIP file: {str(e)}", None)




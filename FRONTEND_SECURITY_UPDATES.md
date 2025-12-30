# Frontend Security Updates - Required Changes

## Overview
Backend now has enhanced security validations. Frontend should update to provide better user experience and prevent errors.

## 1. File Upload Validation

### Blocked File Extensions
The backend now rejects these file extensions:
- Executables: `.exe`, `.bat`, `.cmd`, `.com`, `.scr`, `.vbs`, `.msi`
- Scripts: `.sh`, `.bash`, `.ps1`, `.py`, `.php`, `.js`, `.jar`
- System files: `.dll`, `.so`, `.dylib`, `.sys`
- Archives: `.app`, `.deb`, `.rpm`, `.pkg`, `.dmg`, `.iso`

**Frontend should:**
- Validate file extensions before upload
- Show error message: "File type not allowed. Please upload only .zip or .txt files"
- Disable upload button for invalid files

### Filename Validation
Backend rejects filenames with:
- Path traversal: `../`, `..\\`
- Dangerous characters: `/`, `\`, `:`, `*`, `?`, `"`, `<`, `>`, `|`

**Frontend should:**
- Validate filename before upload
- Show error: "Invalid filename. Please use a valid filename without special characters"

### Error Messages
Backend may return these new error messages:
- `"Invalid or dangerous filename"`
- `"ZIP contains dangerous file type: [filename]"`
- `"ZIP contains invalid or dangerous filename: [filename]"`
- `"ZIP file contains too many entries (possible zip bomb)"`

**Frontend should:**
- Display these error messages clearly to users
- Don't show technical details, show user-friendly messages

## 2. Group Name Validation

### Changes
- Group names are now sanitized (max 200 characters)
- Control characters are removed
- If sanitization results in empty string, backend returns error

**Frontend should:**
- Limit group name input to 200 characters
- Show character count: "X/200 characters"
- Validate before submit: show error if name would be empty after sanitization
- Show warning if user enters special characters: "Special characters will be removed"

## 3. RAG Query Validation

### Changes
- Query text is sanitized (max 5000 characters)
- Control characters are removed
- Session ID limited to 100 characters

**Frontend should:**
- Limit query input to 5000 characters
- Show character count: "X/5000 characters"
- Trim whitespace before sending
- Validate session_id length (should be UUID, max 100 chars)

## 4. Recommended Frontend Validations

### Before File Upload
```typescript
function validateFile(file: File): string | null {
  // Check file size (5MB)
  if (file.size > 5 * 1024 * 1024) {
    return "File size exceeds 5MB limit";
  }
  
  // Check extension
  const ext = file.name.toLowerCase().split('.').pop();
  if (ext !== 'zip' && ext !== 'txt') {
    return "File must be a ZIP or TXT file";
  }
  
  // Check filename
  const dangerousChars = /[<>:"|?*\/\\]/;
  if (dangerousChars.test(file.name) || file.name.includes('..')) {
    return "Invalid filename. Please use a valid filename";
  }
  
  return null; // Valid
}
```

### Before Group Name Submit
```typescript
function validateGroupName(name: string): string | null {
  if (!name || name.trim().length === 0) {
    return "Group name is required";
  }
  
  if (name.length > 200) {
    return "Group name must be 200 characters or less";
  }
  
  // Check if would be empty after sanitization
  const sanitized = name.replace(/[\x00-\x1F\x7F]/g, '').trim();
  if (sanitized.length === 0) {
    return "Group name cannot contain only special characters";
  }
  
  return null; // Valid
}
```

### Before RAG Query Submit
```typescript
function validateRAGQuery(query: string): string | null {
  if (!query || query.trim().length === 0) {
    return "Question cannot be empty";
  }
  
  if (query.length > 5000) {
    return "Question must be 5000 characters or less";
  }
  
  return null; // Valid
}
```

## 5. Error Handling

### File Upload Errors
```typescript
try {
  await uploadFile(file);
} catch (error) {
  if (error.message.includes('dangerous') || 
      error.message.includes('invalid filename')) {
    showError("File rejected for security reasons. Please upload a valid chat file.");
  } else {
    showError(error.message);
  }
}
```

## Summary

**Must Update:**
1. ✅ File extension validation before upload
2. ✅ Filename validation before upload
3. ✅ Group name character limit (200 chars)
4. ✅ RAG query character limit (5000 chars)

**Nice to Have:**
- Character counters for inputs
- Pre-validation warnings
- Better error messages for security rejections

**No Breaking Changes:**
- All existing valid files will still work
- Error messages are backward compatible
- API endpoints unchanged


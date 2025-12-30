# Frontend Quick Fix - Handle HTML Error Responses

## Problem
Backend is returning HTML error pages instead of JSON in production, causing poor error messages.

## Quick Fix for Frontend

### Update Upload Error Handling

**In your upload function (GuestUpload.tsx or similar), update the catch block:**

```typescript
catch (error: any) {
  console.error('[GuestUpload] Upload error:', error);
  
  let errorMessage = 'Upload failed. Please try again.';
  
  // Handle HTML error responses from backend
  if (error.message?.includes('<html>') || 
      error.message?.includes('Internal Server Error') ||
      error.message?.includes('500')) {
    errorMessage = 'Server error occurred. The file may be too large or the server is busy. Please try again in a moment.';
  } else if (error.message?.includes('timeout') || error.name === 'AbortError') {
    errorMessage = 'Upload timeout - file processing is taking longer than expected. Please try again with a smaller file.';
  } else if (error.message) {
    errorMessage = error.message;
  } else if (error.error) {
    errorMessage = error.error;
  }
  
  setError(errorMessage);
}
```

### Better: Check Response Content-Type

**More robust solution - check if response is HTML before parsing:**

```typescript
const handleUpload = async (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  
  try {
    setUploading(true);
    setError(null);
    
    const response = await fetch('/api/metrics/chat/upload-anonymous', {
      method: 'POST',
      body: formData,
    });
    
    if (!response.ok) {
      const contentType = response.headers.get('content-type') || '';
      let errorMessage = `Upload failed (${response.status})`;
      
      if (contentType.includes('application/json')) {
        // JSON error - parse it
        const errorData = await response.json();
        errorMessage = errorData.error || errorMessage;
      } else {
        // HTML or other format - show generic message
        errorMessage = 'Server error occurred. Please try again.';
        
        // For 500 errors with large files, suggest smaller file
        if (response.status === 500 && file.size > 3 * 1024 * 1024) {
          errorMessage = 'File may be too large for processing. Please try a smaller file or split your chat export.';
        }
      }
      
      throw new Error(errorMessage);
    }
    
    const data = await response.json();
    // Handle success...
    
  } catch (error: any) {
    console.error('[GuestUpload] Upload error:', error);
    
    let errorMessage = 'Upload failed. Please try again.';
    
    if (error.name === 'AbortError') {
      errorMessage = 'Upload timeout. Please try again with a smaller file.';
    } else if (error.message) {
      errorMessage = error.message;
    }
    
    setError(errorMessage);
  } finally {
    setUploading(false);
  }
};
```

## Key Points

1. **Check Content-Type header** - If it's `text/html`, don't try to parse as JSON
2. **Show user-friendly messages** - Don't show raw HTML to users
3. **Handle timeouts** - Large files may timeout, suggest smaller files
4. **Provide context** - For 500 errors with large files, suggest file size issue

## User-Friendly Error Messages

```typescript
// Helper function
function getUploadErrorMessage(error: any, fileSize?: number): string {
  // File size issues
  if (fileSize && fileSize > 4 * 1024 * 1024) {
    return 'File is too large. Please use a file smaller than 5MB.';
  }
  
  // Timeout
  if (error.name === 'AbortError' || error.message?.includes('timeout')) {
    return 'Upload timeout - file processing is taking longer than expected. Please try again with a smaller file.';
  }
  
  // Server errors
  if (error.message?.includes('500') || error.message?.includes('Internal Server Error')) {
    return 'Server error occurred. The file may be too large or corrupted. Please try again or contact support.';
  }
  
  // Default
  return error.message || 'Upload failed. Please try again.';
}
```

## Summary

**Tell frontend developer:**

"Update error handling to:
1. Check `response.headers.get('content-type')` before parsing
2. If HTML, show generic user-friendly message
3. For 500 errors with large files (>3MB), suggest file size issue
4. Handle timeout errors with helpful message"

This will improve UX even while backend is being fixed.


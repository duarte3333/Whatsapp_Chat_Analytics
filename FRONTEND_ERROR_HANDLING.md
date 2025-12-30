# Frontend Error Handling Improvements

## Problem
Backend is returning HTML error pages instead of JSON in production, making it difficult to show user-friendly error messages.

## Frontend Solutions

### 1. Detect HTML Error Responses

Add a helper function to detect and parse HTML errors:

```typescript
// utils/errorHandler.ts
export function parseErrorResponse(response: Response, error: any): string {
  // If response is HTML, try to extract error message
  if (response.headers.get('content-type')?.includes('text/html')) {
    // Try to parse HTML error page
    if (typeof error === 'string' && error.includes('<html>')) {
      // Extract title or h1 text if possible
      const titleMatch = error.match(/<title>(.*?)<\/title>/i);
      const h1Match = error.match(/<h1[^>]*>(.*?)<\/h1>/i);
      
      if (titleMatch) {
        return `Server Error: ${titleMatch[1]}`;
      }
      if (h1Match) {
        return `Server Error: ${h1Match[1]}`;
      }
      
      return 'Server error occurred. Please try again or contact support.';
    }
  }
  
  // Try to parse JSON error
  if (error?.error) {
    return error.error;
  }
  
  if (typeof error === 'string') {
    return error;
  }
  
  return 'An unexpected error occurred. Please try again.';
}
```

### 2. Update Upload Function

```typescript
// GuestUpload.tsx or similar
const handleUpload = async (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  
  try {
    setUploading(true);
    setError(null);
    
    const response = await fetch('/api/metrics/chat/upload-anonymous', {
      method: 'POST',
      body: formData,
      // Don't set Content-Type header - browser will set it with boundary
    });
    
    // Check if response is OK
    if (!response.ok) {
      // Try to get error message
      const contentType = response.headers.get('content-type') || '';
      
      let errorMessage = `Upload failed (${response.status})`;
      
      if (contentType.includes('application/json')) {
        // JSON error response
        const errorData = await response.json();
        errorMessage = errorData.error || errorMessage;
      } else if (contentType.includes('text/html')) {
        // HTML error response - try to extract message
        const htmlText = await response.text();
        errorMessage = parseErrorResponse(response, htmlText);
      } else {
        // Try to parse as text
        const text = await response.text();
        try {
          const json = JSON.parse(text);
          errorMessage = json.error || errorMessage;
        } catch {
          errorMessage = text || errorMessage;
        }
      }
      
      throw new Error(errorMessage);
    }
    
    // Parse successful response
    const data = await response.json();
    onUploadSuccess(data);
    
  } catch (error: any) {
    console.error('[GuestUpload] Upload error:', error);
    
    // Handle different error types
    let errorMessage = 'Upload failed. Please try again.';
    
    if (error.name === 'AbortError') {
      errorMessage = 'Upload timeout - file processing is taking longer than expected. Please try again.';
    } else if (error.message) {
      errorMessage = error.message;
    } else if (error.error) {
      errorMessage = error.error;
    }
    
    setError(errorMessage);
  } finally {
    setUploading(false);
  }
};
```

### 3. Better Error Messages for Common Cases

```typescript
// utils/uploadErrors.ts
export function getUploadErrorMessage(error: any, fileSize?: number): string {
  // File size errors
  if (fileSize && fileSize > 5 * 1024 * 1024) {
    return 'File size exceeds 5MB limit. Please use a smaller file.';
  }
  
  // Network errors
  if (error.name === 'TypeError' && error.message.includes('fetch')) {
    return 'Network error. Please check your connection and try again.';
  }
  
  // Timeout errors
  if (error.name === 'AbortError' || error.message?.includes('timeout')) {
    return 'Upload timeout - file processing is taking longer than expected. Please try again with a smaller file.';
  }
  
  // Server errors
  if (error.message?.includes('500') || error.message?.includes('Internal Server Error')) {
    return 'Server error occurred. The file may be too large or corrupted. Please try again or contact support.';
  }
  
  // Bad request
  if (error.message?.includes('400') || error.message?.includes('Bad Request')) {
    return 'Invalid file format. Please upload a valid WhatsApp chat export (ZIP or TXT).';
  }
  
  // Default
  return error.message || 'Upload failed. Please try again.';
}
```

### 4. Add Retry Logic for Transient Errors

```typescript
async function uploadWithRetry(
  file: File,
  maxRetries: number = 2,
  retryDelay: number = 2000
): Promise<any> {
  let lastError: any;
  
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      const formData = new FormData();
      formData.append('file', file);
      
      const response = await fetch('/api/metrics/chat/upload-anonymous', {
        method: 'POST',
        body: formData,
      });
      
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      
      return await response.json();
      
    } catch (error: any) {
      lastError = error;
      
      // Don't retry on client errors (4xx)
      if (error.message?.includes('400') || error.message?.includes('401') || 
          error.message?.includes('403') || error.message?.includes('404')) {
        throw error;
      }
      
      // Retry on server errors (5xx) or network errors
      if (attempt < maxRetries) {
        console.log(`Upload attempt ${attempt + 1} failed, retrying...`);
        await new Promise(resolve => setTimeout(resolve, retryDelay));
        continue;
      }
    }
  }
  
  throw lastError;
}
```

### 5. Show User-Friendly Error Messages

```typescript
// In your component
const [error, setError] = useState<string | null>(null);

// When displaying error
{error && (
  <Alert severity="error" onClose={() => setError(null)}>
    <AlertTitle>Upload Failed</AlertTitle>
    {getUploadErrorMessage(error, file?.size)}
    {error.includes('timeout') && (
      <Box mt={1}>
        <Typography variant="body2">
          Tip: Try uploading a smaller file or splitting your chat export.
        </Typography>
      </Box>
    )}
  </Alert>
)}
```

## Quick Fix: Update Error Handling

**Minimal change to existing code:**

```typescript
// In your upload catch block
catch (error: any) {
  console.error('[GuestUpload] Upload error:', error);
  
  let errorMessage = 'Upload failed. Please try again.';
  
  // Handle HTML error responses
  if (error.message?.includes('<html>') || error.message?.includes('Internal Server Error')) {
    errorMessage = 'Server error occurred. The file may be too large or the server is busy. Please try again in a moment.';
  } else if (error.message) {
    errorMessage = error.message;
  }
  
  setError(errorMessage);
}
```

## Summary

The frontend can:
1. ✅ Detect HTML error responses
2. ✅ Extract meaningful error messages from HTML
3. ✅ Show user-friendly error messages
4. ✅ Handle different error types (timeout, network, server)
5. ✅ Provide retry logic for transient errors
6. ✅ Give helpful tips based on error type

This improves UX even when backend returns HTML errors, while you fix the backend to return JSON.


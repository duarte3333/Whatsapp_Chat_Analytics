# Rate Limiting Implementation

## Overview
Rate limiting has been implemented to prevent abuse and protect the API from brute force attacks and excessive usage.

## Backend (Spring Boot)

### Dependencies Added
- `bucket4j-core:8.10.1` - Core rate limiting library
- `bucket4j-jcache:8.10.1` - JCache integration
- `caffeine:3.1.8` - High-performance caching

### Rate Limits

#### Anonymous Users (by IP)
- **10 requests per minute**
- **50 requests per hour**

#### Authenticated Users (by email)
- **30 requests per minute**
- **200 requests per hour**

#### File Uploads (all users)
- **3 uploads per hour**

#### Authentication Endpoints (prevent brute force)
- **5 attempts per 15 minutes**

### Implementation
- `RateLimitConfig.java` - Configures rate limit buckets
- `RateLimitFilter.java` - Applies rate limiting to all requests
- Integrated into Spring Security filter chain

### Response Headers
When rate limit is active, response includes:
- `X-RateLimit-Remaining`: Number of requests remaining

### Error Response
When rate limit is exceeded:
- **Status**: `429 Too Many Requests`
- **Body**: `{"error": "Rate limit exceeded. Please try again later."}`

## Python Microservice (Flask)

### Dependencies Added
- `Flask-Limiter==3.8.0` - Rate limiting for Flask

### Rate Limits

#### Default Limits (all endpoints)
- **30 requests per minute**
- **200 requests per hour**

#### File Upload Endpoints (`/analyze`, `/rag/upload`)
- **3 uploads per hour**

#### RAG Query Endpoint (`/rag/ask`)
- **30 requests per minute**
- **200 requests per hour**

### Implementation
- Uses Flask-Limiter with in-memory storage
- Keyed by IP address (`X-Forwarded-For` or `X-Real-IP`)

### Error Response
When rate limit is exceeded:
- **Status**: `429 Too Many Requests`
- **Body**: JSON error message

## Frontend Considerations

### Handling Rate Limit Errors

```typescript
try {
  await apiCall();
} catch (error) {
  if (error.response?.status === 429) {
    // Rate limit exceeded
    const retryAfter = error.response.headers['retry-after'];
    showError(`Too many requests. Please wait ${retryAfter} seconds.`);
  }
}
```

### Best Practices
1. **Implement exponential backoff** when receiving 429 errors
2. **Show user-friendly messages** explaining rate limits
3. **Display remaining requests** if available in headers
4. **Cache responses** to reduce API calls
5. **Batch requests** when possible

## Production Recommendations

### Backend
- **Use Redis** instead of in-memory cache for distributed rate limiting
- **Configure NGINX** rate limiting as additional layer
- **Monitor rate limit hits** to detect abuse patterns

### Python Microservice
- **Use Redis** for Flask-Limiter storage in production:
  ```python
  limiter = Limiter(
      app=app,
      key_func=get_remote_address,
      storage_uri="redis://localhost:6379"
  )
  ```

## Testing Rate Limits

### Test Anonymous Limits
```bash
# Should fail after 10 requests in 1 minute
for i in {1..15}; do
  curl -X POST http://localhost:8080/api/metrics/chat/upload-anonymous
done
```

### Test Authenticated Limits
```bash
# With JWT token
for i in {1..35}; do
  curl -X GET -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/user/groups
done
```

### Test Upload Limits
```bash
# Should fail after 3 uploads in 1 hour
for i in {1..5}; do
  curl -X POST -F "file=@chat.zip" http://localhost:8080/api/metrics/chat/upload-anonymous
done
```

## Configuration

Rate limits can be adjusted in:
- **Backend**: `RateLimitConfig.java` - Modify bucket configurations
- **Python**: `analytics_service.py` - Modify `@limiter.limit()` decorators

## Notes

- Rate limits are **per IP address** for anonymous users
- Rate limits are **per email** for authenticated users
- Limits reset automatically based on time windows
- OPTIONS requests are excluded from rate limiting (CORS preflight)


## Project Description

This project analyzes WhatsApp chat data to compute various statistics and metrics. Users can upload their chat backups, and the backend service processes them—either for authenticated users or anonymously—extracting insights such as message counts, chat activity, and other conversation statistics. A Python microservice handles the analytics computations, while the backend manages user authentication, file storage, and metrics retrieval.



## Testing the Application

### 1) Run the backend
```bash
cd backend
./gradlew build
./gradlew bootRun
```

### 2) Run the Python microservice
```bash
cd python_microservice
python3 analytics_service.py
```

### 3) Login with dummy user
```bash
curl -X POST "http://localhost:8080/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
        "email": "testuser@example.com",
        "password": "password123"
      }'
```

The response will include a JWT token. Copy this token for the next request.

### 4) Send authorized request
```bash
curl -v -X POST "http://localhost:8080/metrics/chat/upload" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "file=@/path/to/your/chat_file.zip"
```

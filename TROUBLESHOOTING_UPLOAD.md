# Troubleshooting Upload Issues

## Error: "Connection refused" on port 5050

### Problem
The Spring Boot backend cannot connect to the Python microservice because it's not running.

### Solution

1. **Check if Python microservice is running:**
   ```bash
   # Check if port 5050 is in use
   lsof -i :5050
   # OR
   netstat -an | grep 5050
   ```

2. **Start the Python microservice:**

   **Option A: Using Gunicorn (Production)**
   ```bash
   cd python_microservice
   source venv/bin/activate
   gunicorn -w 1 -b 127.0.0.1:5050 --timeout 300 analytics_service:application
   ```

   **Option B: Using Flask dev server (Development)**
   ```bash
   cd python_microservice
   source venv/bin/activate
   python analytics_service.py
   # OR if you have a run script
   python -m flask run --host=127.0.0.1 --port=5050
   ```

3. **Verify it's running:**
   ```bash
   curl http://localhost:5050/analyze
   # Should return an error about missing file, not "Connection refused"
   ```

4. **Check logs:**
   ```bash
   # If using systemd
   sudo journalctl -u python-microservice -f
   
   # If running manually, check terminal output
   ```

### Common Issues

#### Issue 1: Port already in use
```bash
# Find process using port 5050
lsof -i :5050
# Kill it
kill -9 <PID>
```

#### Issue 2: Missing dependencies
```bash
cd python_microservice
source venv/bin/activate
pip install -r requirements.txt
```

#### Issue 3: Wrong Python version
```bash
# Check Python version (should be 3.8+)
python --version
```

#### Issue 4: SSL/HTTPS configuration
If you're using HTTPS locally, make sure the truststore is configured correctly in `application.properties`.

### Quick Test

Test the Python service directly:
```bash
# Test if service responds
curl -X POST http://localhost:5050/analyze \
  -F "file=@test_chat.txt"

# Should return JSON response or error about file format, not connection error
```

### Rate Limiting Note

If you see `429 Too Many Requests`, that's rate limiting working correctly. The "Connection refused" error is different - it means the service isn't running at all.

### Development Setup

For local development, you typically need:
1. **Backend (Spring Boot)** - Port 8080
2. **Python Microservice** - Port 5050
3. **Frontend** - Port 3000/8081
4. **PostgreSQL** - Port 5432

All services must be running for uploads to work.



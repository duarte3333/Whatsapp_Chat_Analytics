# Deploy Checklist - Fix JSON Error Responses

## Critical Steps for Production Deployment

### 1. Verify Code is Deployed

```bash
# SSH into EC2
ssh ec2-user@your-server

# Navigate to project
cd /home/ec2-user/Whatsapp_Chat_Analytics

# Pull latest changes
git pull origin main

# Verify files exist
ls -la python_microservice/analytics_service.py
ls -la python_microservice/gunicorn_config.py
```

### 2. Check Service Configuration

```bash
# View current service file
sudo cat /etc/systemd/system/python-microservice.service
```

**Must use:** `analytics_service:application` (NOT `analytics_service:app`)

**Example correct service file:**
```ini
[Unit]
Description=WhatsApp Analytics Python Microservice
After=network.target

[Service]
Type=notify
User=ec2-user
WorkingDirectory=/home/ec2-user/Whatsapp_Chat_Analytics/python_microservice
Environment="PATH=/home/ec2-user/Whatsapp_Chat_Analytics/python_microservice/venv/bin"
ExecStart=/home/ec2-user/Whatsapp_Chat_Analytics/python_microservice/venv/bin/gunicorn \
    --config gunicorn_config.py \
    --bind 127.0.0.1:5050 \
    --timeout 300 \
    analytics_service:application
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### 3. Verify Python Code Has `application` Variable

```bash
# Check if analytics_service.py has the application variable
grep -n "application = " python_microservice/analytics_service.py
```

Should show:
```
1234:application = JSONErrorHandler(app)
```

### 4. Test Syntax

```bash
cd python_microservice
source venv/bin/activate
python3 -c "import analytics_service; print('OK')"
```

### 5. Restart Service

```bash
# Reload systemd
sudo systemctl daemon-reload

# Restart service
sudo systemctl restart python-microservice

# Check status
sudo systemctl status python-microservice
```

### 6. Check Logs for Errors

```bash
# View recent logs
sudo journalctl -u python-microservice --since "2 minutes ago" | tail -100

# Follow logs in real-time
sudo journalctl -u python-microservice -f
```

### 7. Test Endpoint Directly

```bash
# Test from server
curl -X POST http://localhost:5050/analyze \
  -F "file=@/path/to/test.zip" \
  -v 2>&1 | head -50
```

**Expected:** Should return JSON, not HTML

### 8. Verify Gunicorn Config

```bash
cat python_microservice/gunicorn_config.py
```

Should have timeout settings:
```python
timeout = 300  # 5 minutes
```

## Common Issues

### Issue 1: Service Still Using Old Entry Point

**Symptom:** Errors still return HTML

**Fix:**
```bash
# Edit service file
sudo nano /etc/systemd/system/python-microservice.service

# Change: analytics_service:app
# To: analytics_service:application

# Reload and restart
sudo systemctl daemon-reload
sudo systemctl restart python-microservice
```

### Issue 2: Code Not Deployed

**Symptom:** Changes not reflected

**Fix:**
```bash
cd /home/ec2-user/Whatsapp_Chat_Analytics
git pull
sudo systemctl restart python-microservice
```

### Issue 3: Syntax Error Preventing Import

**Symptom:** Service fails to start

**Fix:**
```bash
cd python_microservice
source venv/bin/activate
python3 -m py_compile analytics_service.py
# Fix any syntax errors shown
```

### Issue 4: Memory Issues with Large Files

**Symptom:** Service crashes on large files

**Check:**
```bash
# Check available memory
free -h

# Check service memory usage
sudo systemctl status python-microservice
# Look at "Memory:" line
```

**Fix:** May need to increase EC2 instance memory or optimize code

## Verification

After deployment, test with:

```bash
# Small file (should work)
curl -X POST http://localhost:5050/analyze -F "file=@small.zip" -v

# Large file (4MB, should work)
curl -X POST http://localhost:5050/analyze -F "file=@large.zip" -v
```

Both should return JSON responses (success or error), never HTML.

## Quick Verification Script

```bash
#!/bin/bash
# test_service.sh

echo "Testing Python microservice..."

# Test 1: Check service is running
if systemctl is-active --quiet python-microservice; then
    echo "✅ Service is running"
else
    echo "❌ Service is not running"
    exit 1
fi

# Test 2: Check endpoint responds
response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:5050/analyze)
if [ "$response" != "000" ]; then
    echo "✅ Endpoint is responding (HTTP $response)"
else
    echo "❌ Endpoint is not responding"
    exit 1
fi

# Test 3: Check for application variable
if grep -q "application = JSONErrorHandler" python_microservice/analytics_service.py; then
    echo "✅ application variable exists"
else
    echo "❌ application variable not found"
    exit 1
fi

echo "All checks passed!"
```


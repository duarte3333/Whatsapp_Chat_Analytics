# Deploy to Production - Fix JSON Errors

## Problem
Files < 5MB work locally but fail in production with HTML error responses instead of JSON.

## Step-by-Step Deploy

### 1. SSH into EC2 Server

```bash
ssh ec2-user@your-server-ip
```

### 2. Navigate and Pull Latest Code

```bash
cd /home/ec2-user/Whatsapp_Chat_Analytics
git pull origin main
```

### 3. Verify Critical Files Exist

```bash
# Check analytics_service.py has application variable
grep "application = JSONErrorHandler" python_microservice/analytics_service.py

# Should output: application = JSONErrorHandler(app)

# Check gunicorn config exists
ls -la python_microservice/gunicorn_config.py
```

### 4. Check Current Service Configuration

```bash
sudo cat /etc/systemd/system/python-microservice.service
```

**CRITICAL:** Must use `analytics_service:application` (NOT `analytics_service:app`)

### 5. Update Service File (if needed)

```bash
sudo nano /etc/systemd/system/python-microservice.service
```

**Ensure ExecStart line uses:**
```
ExecStart=.../gunicorn ... analytics_service:application
```

**NOT:**
```
ExecStart=.../gunicorn ... analytics_service:app
```

### 6. Reload and Restart

```bash
# Reload systemd to pick up service file changes
sudo systemctl daemon-reload

# Restart the service
sudo systemctl restart python-microservice

# Wait a few seconds, then check status
sleep 3
sudo systemctl status python-microservice
```

### 7. Verify Service is Running

```bash
# Should show "active (running)"
sudo systemctl status python-microservice | grep "Active:"

# Check logs for any startup errors
sudo journalctl -u python-microservice --since "2 minutes ago" | tail -30
```

### 8. Test Endpoint

```bash
# Test from server (should return JSON error, not HTML)
curl -X POST http://localhost:5050/analyze \
  -F "file=@/path/to/test.zip" \
  -v 2>&1 | head -30
```

**Expected:** JSON response (even on error), NOT HTML

### 9. Check Logs for Real Errors

```bash
# Follow logs in real-time
sudo journalctl -u python-microservice -f
```

Then try uploading from frontend and watch for actual error messages.

## Troubleshooting

### If Still Getting HTML Errors

**Check 1: Service using correct entry point**
```bash
sudo systemctl status python-microservice | grep "gunicorn"
# Should show: analytics_service:application
```

**Check 2: Code actually deployed**
```bash
cd /home/ec2-user/Whatsapp_Chat_Analytics
git log -1 --oneline
# Should show recent commit with JSON error handler
```

**Check 3: Python can import module**
```bash
cd python_microservice
source venv/bin/activate
python3 -c "from analytics_service import application; print('OK')"
```

**Check 4: Syntax errors**
```bash
python3 -m py_compile analytics_service.py
# Should not output anything if OK
```

### If Service Won't Start

```bash
# Check detailed error
sudo journalctl -u python-microservice -n 50 --no-pager

# Common issues:
# - Missing dependencies: pip install -r requirements.txt
# - Syntax errors: python3 -m py_compile analytics_service.py
# - Wrong path: check ExecStart paths in service file
```

### If Files > 3MB Still Fail

**Check timeout settings:**
```bash
# In service file, ensure:
--timeout 300

# In gunicorn_config.py, ensure:
timeout = 300
```

**Check memory:**
```bash
free -h
# If low memory, may need larger EC2 instance
```

**Check NGINX timeout:**
```bash
sudo grep -r "proxy_read_timeout" /etc/nginx/
# Should be at least 300s
```

## Quick Verification Commands

```bash
# All-in-one check
echo "=== Service Status ==="
sudo systemctl status python-microservice --no-pager | head -10

echo -e "\n=== Entry Point Check ==="
sudo systemctl status python-microservice --no-pager | grep "application"

echo -e "\n=== Code Check ==="
cd /home/ec2-user/Whatsapp_Chat_Analytics
git log -1 --oneline

echo -e "\n=== Application Variable ==="
grep "application = " python_microservice/analytics_service.py

echo -e "\n=== Test Endpoint ==="
curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:5050/analyze
```

## Expected Result

After deployment:
- ✅ Errors return JSON (not HTML)
- ✅ Files < 5MB work correctly
- ✅ Error messages are user-friendly
- ✅ Logs show actual error details

## Next Steps After Deploy

1. Test with small file (< 1MB) - should work
2. Test with medium file (2-3MB) - should work
3. Test with large file (4MB) - should work
4. Check logs for any warnings
5. Monitor memory usage during processing


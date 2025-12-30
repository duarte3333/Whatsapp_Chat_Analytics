# Debug 500 Error - Python Microservice

## Problem
The Python microservice is returning a 500 Internal Server Error with HTML response instead of JSON when uploading files.

## Changes Made

### 1. Added Global Error Handler
- Added a Flask error handler for 500 errors to ensure JSON responses
- All errors now return JSON instead of HTML

### 2. Enhanced Logging
- Added detailed logging at the start of `/analyze` endpoint
- Logs file name, size, and processing steps
- Better error messages with traceback

### 3. Error Handling for Chart Data
- Added try-catch around `compute_chart_data()` 
- Returns default/empty chart data if computation fails instead of crashing

## How to Debug

### 1. Check Python Microservice Logs

**If running directly:**
```bash
cd python_microservice
python3 analytics_service.py
# Watch console output for errors
```

**If running as systemd service:**
```bash
# View logs
sudo journalctl -u python-microservice -f

# Or check service status
sudo systemctl status python-microservice
```

### 2. Common Issues

**Issue: Missing dependencies**
```bash
cd python_microservice
pip3 install -r requirements.txt
```

**Issue: Parser import error**
- Check if `stats_computation/parser.py` exists
- Verify Python path is correct

**Issue: Memory error with large files**
- Check available memory: `free -h`
- May need to increase system memory or optimize processing

**Issue: File parsing error**
- Check if the uploaded file is a valid WhatsApp export
- Verify file format (ZIP or TXT)

### 3. Test the Endpoint Directly

```bash
# Test with curl
curl -X POST http://localhost:5050/analyze \
  -F "file=@/path/to/test_file.zip" \
  -v

# Should return JSON, not HTML
```

### 4. Check Backend Logs

The backend forwards requests to Python service. Check backend logs:
```bash
# If running as systemd
sudo journalctl -u backend -f

# Look for errors related to Python service calls
```

## Expected Behavior After Fix

1. **All errors return JSON:**
   ```json
   {
     "error": "Error message here",
     "details": "Full traceback if debug mode"
   }
   ```

2. **Detailed logging:**
   - File name and size logged
   - Processing steps logged
   - Errors include full traceback

3. **Graceful degradation:**
   - If chart computation fails, returns empty chart data
   - If RAG fails, continues without RAG
   - File parsing errors return clear error messages

## Next Steps

1. **Restart Python microservice:**
   ```bash
   sudo systemctl restart python-microservice
   ```

2. **Check logs immediately after restart:**
   ```bash
   sudo journalctl -u python-microservice -f
   ```

3. **Try uploading a file again and watch logs**

4. **If error persists, check:**
   - Python version (should be 3.8+)
   - All dependencies installed
   - File permissions
   - Disk space available

## Quick Fix Commands

```bash
# Restart Python service
sudo systemctl restart python-microservice

# Check if service is running
sudo systemctl status python-microservice

# View recent errors
sudo journalctl -u python-microservice --since "10 minutes ago" | grep -i error

# Test endpoint
curl -X POST http://localhost:5050/analyze -F "file=@test.zip" -v
```


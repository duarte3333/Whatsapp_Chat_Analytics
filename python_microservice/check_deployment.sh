#!/bin/bash
# Script to check if deployment is correct

echo "=== Python Microservice Deployment Check ==="
echo ""

# Check 1: Service status
echo "1. Checking service status..."
if systemctl is-active --quiet python-microservice; then
    echo "   ✅ Service is running"
else
    echo "   ❌ Service is NOT running"
    echo "   Run: sudo systemctl start python-microservice"
    exit 1
fi

# Check 2: Service file entry point
echo ""
echo "2. Checking service file entry point..."
SERVICE_FILE="/etc/systemd/system/python-microservice.service"
if [ -f "$SERVICE_FILE" ]; then
    if grep -q "analytics_service:application" "$SERVICE_FILE"; then
        echo "   ✅ Using correct entry point: analytics_service:application"
    elif grep -q "analytics_service:app" "$SERVICE_FILE"; then
        echo "   ❌ Using WRONG entry point: analytics_service:app"
        echo "   Must change to: analytics_service:application"
        echo "   Edit: sudo nano $SERVICE_FILE"
        echo "   Then: sudo systemctl daemon-reload && sudo systemctl restart python-microservice"
        exit 1
    else
        echo "   ⚠️  Could not find entry point in service file"
    fi
else
    echo "   ⚠️  Service file not found at $SERVICE_FILE"
fi

# Check 3: Code has application variable
echo ""
echo "3. Checking if code has application variable..."
cd /home/ec2-user/Whatsapp_Chat_Analytics 2>/dev/null || cd /home/duarte33/Documents/whatsapp_repo 2>/dev/null || pwd
if grep -q "application = JSONErrorHandler" python_microservice/analytics_service.py 2>/dev/null; then
    echo "   ✅ Code has application variable"
else
    echo "   ❌ Code does NOT have application variable"
    echo "   Need to pull latest code: git pull"
    exit 1
fi

# Check 4: Python can import
echo ""
echo "4. Checking if Python can import module..."
cd python_microservice
if [ -d "venv" ]; then
    source venv/bin/activate
    if python3 -c "from analytics_service import application; print('OK')" 2>/dev/null; then
        echo "   ✅ Python can import application"
    else
        echo "   ❌ Python cannot import application"
        echo "   Check for syntax errors: python3 -m py_compile analytics_service.py"
        exit 1
    fi
else
    echo "   ⚠️  venv not found, skipping import test"
fi

# Check 5: Endpoint responds
echo ""
echo "5. Checking if endpoint responds..."
response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:5050/analyze 2>/dev/null)
if [ "$response" = "405" ] || [ "$response" = "400" ]; then
    echo "   ✅ Endpoint is responding (HTTP $response - expected for GET)"
elif [ "$response" = "000" ]; then
    echo "   ❌ Endpoint is NOT responding"
    echo "   Service may not be running correctly"
    exit 1
else
    echo "   ⚠️  Endpoint returned HTTP $response"
fi

# Check 6: Recent logs
echo ""
echo "6. Checking recent logs for errors..."
recent_errors=$(sudo journalctl -u python-microservice --since "5 minutes ago" 2>/dev/null | grep -i "error\|exception\|traceback" | tail -5)
if [ -z "$recent_errors" ]; then
    echo "   ✅ No recent errors in logs"
else
    echo "   ⚠️  Recent errors found:"
    echo "$recent_errors" | sed 's/^/      /'
fi

echo ""
echo "=== Check Complete ==="
echo ""
echo "If all checks passed but still getting HTML errors:"
echo "1. Verify service was restarted: sudo systemctl restart python-microservice"
echo "2. Check logs: sudo journalctl -u python-microservice -f"
echo "3. Test directly: curl -X POST http://localhost:5050/analyze -F 'file=@test.zip' -v"



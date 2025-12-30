#!/bin/bash

echo "=== Checking NGINX Timeout Configuration ==="
echo ""

# Check if NGINX config exists
if [ ! -f /etc/nginx/conf.d/frontend.conf ]; then
    echo "❌ ERROR: /etc/nginx/conf.d/frontend.conf not found!"
    exit 1
fi

echo "📋 Current NGINX Configuration:"
echo ""

# Check for timeout settings in Python location block
echo "1. Checking Python microservice location block (~ ^/(rag|analyze|metrics|llm)/):"
if sudo grep -A 10 "location ~.*rag\|analyze\|metrics\|llm" /etc/nginx/conf.d/frontend.conf | grep -q "proxy_read_timeout"; then
    echo "   ✅ proxy_read_timeout found"
    sudo grep -A 10 "location ~.*rag\|analyze\|metrics\|llm" /etc/nginx/conf.d/frontend.conf | grep "proxy_read_timeout"
else
    echo "   ❌ proxy_read_timeout NOT FOUND - NEEDS TO BE ADDED!"
fi

if sudo grep -A 10 "location ~.*rag\|analyze\|metrics\|llm" /etc/nginx/conf.d/frontend.conf | grep -q "proxy_send_timeout"; then
    echo "   ✅ proxy_send_timeout found"
    sudo grep -A 10 "location ~.*rag\|analyze\|metrics\|llm" /etc/nginx/conf.d/frontend.conf | grep "proxy_send_timeout"
else
    echo "   ❌ proxy_send_timeout NOT FOUND - NEEDS TO BE ADDED!"
fi

if sudo grep -A 10 "location ~.*rag\|analyze\|metrics\|llm" /etc/nginx/conf.d/frontend.conf | grep -q "proxy_connect_timeout"; then
    echo "   ✅ proxy_connect_timeout found"
    sudo grep -A 10 "location ~.*rag\|analyze\|metrics\|llm" /etc/nginx/conf.d/frontend.conf | grep "proxy_connect_timeout"
else
    echo "   ❌ proxy_connect_timeout NOT FOUND - NEEDS TO BE ADDED!"
fi

echo ""
echo "2. Checking server-level timeouts:"
if sudo grep -q "client_body_timeout" /etc/nginx/conf.d/frontend.conf; then
    echo "   ✅ client_body_timeout found"
    sudo grep "client_body_timeout" /etc/nginx/conf.d/frontend.conf
else
    echo "   ⚠️  client_body_timeout not found (optional but recommended)"
fi

echo ""
echo "3. Testing NGINX configuration:"
if sudo nginx -t 2>&1 | grep -q "successful"; then
    echo "   ✅ NGINX configuration is valid"
    sudo nginx -t
else
    echo "   ❌ NGINX configuration has errors!"
    sudo nginx -t
    exit 1
fi

echo ""
echo "4. Checking if NGINX is running:"
if systemctl is-active --quiet nginx; then
    echo "   ✅ NGINX is running"
else
    echo "   ❌ NGINX is NOT running!"
fi

echo ""
echo "=== Summary ==="
echo "If you see ❌ above, you need to add the missing timeout settings."
echo "See NGINX_FIX_NOW.md for instructions."


# Production Deployment Update Guide

## 1. Access the Production Instance
```bash
ssh username@instance-public-ipv4
```

## 2. Development Workflow

### Local Development
1. Make the required code changes locally
2. Test changes locally
3. Commit and push to the repository
```bash
git add .
git commit -m "Your commit message"
git push origin main
```

## 3. Production Deployment

### Backend Deployment (Spring Boot)
```bash
# Navigate to backend directory
cd /path/to/backend

# Pull latest changes
git pull

# Build the application
./gradlew bootJar

# Restart the backend service
sudo systemctl restart backend

# Verify deployment
sudo systemctl status backend
```

### Frontend Deployment (React/Vite)
```bash
# Navigate to frontend directory
cd /path/to/frontend

# Stop backend to free up resources for build
sudo systemctl stop backend

# Pull latest changes
git pull

# Install dependencies (if package.json changed)
npm install

# Build for production
npm run build

# Start backend again
sudo systemctl start backend

# Restart nginx to serve new build
sudo systemctl restart nginx

# Verify deployment
sudo systemctl status nginx
```

## 4. Verification Steps

### Check Services
```bash
# Check backend logs
sudo journalctl -u backend --since "5 minutes ago"

# Check nginx logs
sudo journalctl -u nginx --since "5 minutes ago"

# Test API endpoint
curl -X GET https://chat-lytics.com/api/health
```

### Troubleshooting
```bash
# If backend fails to start
sudo journalctl -u backend -f

# If nginx fails
sudo nginx -t
sudo journalctl -u nginx -f
```
# Production Deployment Update Guide

## 1. Access the Production Instance
```bash
ssh username@instance-public-ipv4

## 2. Backend Deployment (Spring Boot)
Local

1. Make the required code changes locally
2. Commit and push to the repository

Production

Backend
1. git pull
2. ./gradlew bootJar
3. sudo systemctl restart backend

Frontend
1. git pull
2. npm run build
3. sudo systemctl restart nginx





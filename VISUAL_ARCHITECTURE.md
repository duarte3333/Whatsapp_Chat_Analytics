# 🎨 Visual Architecture - Quick Reference

## System Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         🌐 USER BROWSER                                  │
│                    http://localhost:3000                                 │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               │ HTTP Requests (CORS enabled)
                               │
┌──────────────────────────────▼──────────────────────────────────────────┐
│                    🔵 REACT FRONTEND                                    │
│                   localhost:3000                                        │
│                                                                          │
│  Routes:                                                                 │
│  ├─ /              → Dashboard                                          │
│  ├─ /upload        → File Upload                                        │
│  ├─ /analytics/:id → Chat Analytics                                     │
│  └─ /login         → Authentication                                     │
│                                                                          │
│  Components:                                                             │
│  ├─ Dashboard.tsx                                                      │
│  ├─ BarChart.tsx                                                        │
│  ├─ LineChartByWeek.tsx                                                 │
│  └─ CSVReader.tsx                                                       │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               │ POST /metrics/chat/upload-anonymous
                               │ POST /metrics/chat/upload (with JWT)
                               │ GET  /metrics/chat/:chatId
                               │ POST /auth/login
                               │ POST /chat-llm/:chatId
                               │
┌──────────────────────────────▼──────────────────────────────────────────┐
│              🟢 SPRING BOOT BACKEND                                     │
│              localhost:8080                                             │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────┐         │
│  │  Controllers:                                              │         │
│  │  ├─ AuthenticationController (/auth/*)                   │         │
│  │  ├─ ChatMetricsController (/metrics/*)                    │         │
│  │  ├─ ChatLLMController (/chat-llm/*)                       │         │
│  │  └─ UserController (/users/*)                             │         │
│  └────────────────────────────────────────────────────────────┘         │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────┐         │
│  │  Services:                                                 │         │
│  │  ├─ AuthenticationService                                 │         │
│  │  │   ├─ JWT Generation                                     │         │
│  │  │   ├─ OAuth2 (Google/Microsoft)                         │         │
│  │  │   └─ Email Verification                                 │         │
│  │  ├─ ChatMetricsService                                    │         │
│  │  │   └─ Forwards files to Python                          │         │
│  │  └─ ChatLLMService                                         │         │
│  │      └─ Forwards queries to Python                        │         │
│  └────────────────────────────────────────────────────────────┘         │
│                                                                          │
│  Security:                                                               │
│  ├─ JWT Authentication                                                  │
│  ├─ Spring Security Filter Chain                                        │
│  └─ CORS: Allows all origins (*)                                       │
└──────────────┬──────────────────────────────┬──────────────────────────┘
               │                              │
               │ HTTP POST                    │ HTTP POST
               │ http://localhost:5050/analyze│ http://localhost:5050/rag/*
               │                              │
┌──────────────▼──────────────────────────────▼──────────────────────────┐
│              🐍 PYTHON MICROSERVICE                                      │
│              localhost:5050                                             │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────┐         │
│  │  Flask Endpoints:                                          │         │
│  │  ├─ POST /analyze                                          │         │
│  │  │   └─ Main analytics endpoint                            │         │
│  │  ├─ POST /rag/upload                                       │         │
│  │  │   └─ Create RAG session                                │         │
│  │  ├─ POST /rag/ask                                          │         │
│  │  │   └─ Query chat with AI                                │         │
│  │  └─ GET  /rag/status/:session_id                          │         │
│  └────────────────────────────────────────────────────────────┘         │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────┐         │
│  │  Processing Pipeline:                                      │         │
│  │  1. Extract ZIP file                                       │         │
│  │  2. Parse WhatsApp chat (WhatsAppChatParser)              │         │
│  │  3. Compute analytics:                                     │         │
│  │     ├─ Message counts per user                            │         │
│  │     ├─ Weekly volume                                      │         │
│  │     ├─ Heatmap (day × hour)                               │         │
│  │     ├─ Emoji statistics                                   │         │
│  │     └─ Streak calculations                                │         │
│  │  4. Create RAG session (if enabled)                        │         │
│  │  5. Return JSON response                                   │         │
│  └────────────────────────────────────────────────────────────┘         │
│                                                                          │
│  RAG Cache:                                                              │
│  ├─ TTL: 10 minutes                                                     │
│  ├─ Token limit: 128k tokens                                           │
│  └─ Stores most recent messages                                        │
└──────────────┬──────────────────────────────────────────────────────────┘
               │
               │ OpenAI API Call
               │ (GPT-4o-mini)
               │
┌──────────────▼──────────────────────────────────────────────────────────┐
│              🤖 OPENAI API                                              │
│              api.openai.com                                             │
│              GPT-4o-mini                                                │
└──────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│              💾 POSTGRESQL DATABASE                                    │
│              localhost:5432                                             │
│                                                                          │
│  Tables:                                                                 │
│  ├─ users                                                                │
│  │   ├─ id, email, username, password_hash                             │
│  │   └─ verified, created_at                                           │
│  ├─ group_chats                                                         │
│  │   ├─ id, title, user_id                                             │
│  │   └─ participants_count, created_at                                 │
│  └─ chat_metrics                                                        │
│      ├─ id, group_chat_id                                              │
│      ├─ total_messages, top_user                                       │
│      └─ JSON columns (chart data)                                      │
└─────────────────────────────────────────────────────────────────────────┘
```

## 🔄 Complete Data Flow

### Flow 1: Anonymous Upload
```
User Browser
    │
    │ 1. Upload ZIP file
    ▼
React Frontend (localhost:3000)
    │
    │ 2. POST /metrics/chat/upload-anonymous
    │    Content-Type: multipart/form-data
    │    Body: file=chat.zip
    ▼
Spring Boot Backend (localhost:8080)
    │
    │ 3. POST http://localhost:5050/analyze
    │    Body: file=chat.zip
    ▼
Python Microservice (localhost:5050)
    │
    │ 4. Extract ZIP → Parse chat → Compute stats
    │
    │ 5. Return JSON:
    │    {
    │      "total_messages": 15234,
    │      "messagesPerUser": [...],
    │      "weeklyVolume": [...],
    │      "heatmapData": [...],
    │      ...
    │    }
    ▼
Spring Boot Backend
    │
    │ 6. Forward JSON response
    ▼
React Frontend
    │
    │ 7. Render charts and statistics
    ▼
User Browser (sees analytics)
```

### Flow 2: Authenticated Upload with Database
```
User Browser
    │
    │ 1. POST /auth/login
    │    {email, password}
    ▼
Spring Boot Backend
    │
    │ 2. Validate credentials → Generate JWT
    │
    │ 3. Return {token, expiresIn}
    ▼
React Frontend (stores JWT)
    │
    │ 4. POST /metrics/chat/upload
    │    Authorization: Bearer {token}
    │    Body: file=chat.zip
    ▼
Spring Boot Backend
    │
    │ 5. Validate JWT → Extract user
    │
    │ 6. POST http://localhost:5050/analyze?enable_rag=true
    ▼
Python Microservice
    │
    │ 7. Process file → Create RAG session
    │
    │ 8. Return stats + session_id
    ▼
Spring Boot Backend
    │
    │ 9. Save to database:
    │    - INSERT INTO group_chats
    │    - INSERT INTO chat_metrics
    │
    │ 10. Return {stats, chatId, session_id}
    ▼
React Frontend
    │
    │ 11. Display analytics + save chatId
    ▼
User Browser
```

### Flow 3: RAG Query (Ask Question)
```
User Browser
    │
    │ 1. Type question: "Who talks more?"
    ▼
React Frontend
    │
    │ 2. POST /chat-llm/:chatId
    │    Authorization: Bearer {token}
    │    Body: {question: "Who talks more?"}
    ▼
Spring Boot Backend
    │
    │ 3. Validate JWT → Verify user owns chat
    │
    │ 4. POST http://localhost:5050/rag/ask
    │    Body: {session_id, question}
    ▼
Python Microservice
    │
    │ 5. Retrieve RAG session from cache
    │    (contains chat context, 128k tokens)
    │
    │ 6. POST https://api.openai.com/v1/chat/completions
    │    Model: gpt-4o-mini
    │    Prompt: [chat context] + question
    ▼
OpenAI API
    │
    │ 7. Generate answer using chat context
    │
    │ 8. Return {answer: "Based on the messages..."}
    ▼
Python Microservice
    │
    │ 9. Return {answer, messages_used, expires_in_seconds}
    ▼
Spring Boot Backend
    │
    │ 10. Forward answer
    ▼
React Frontend
    │
    │ 11. Display answer to user
    ▼
User Browser (sees answer)
```

## 📍 Port Summary

| Service | Port | Protocol | Purpose |
|---------|------|----------|---------|
| React Frontend | 3000 | HTTP | User interface |
| Spring Boot Backend | 8080 | HTTP | API gateway, auth, database |
| Python Microservice | 5050 | HTTP | Analytics, RAG, AI |
| PostgreSQL | 5432 | TCP | Database |
| SMTP (Gmail) | 587 | TCP | Email verification |

## 🔐 Authentication Flow

```
┌─────────────┐
│   User      │
└──────┬──────┘
       │
       │ 1. POST /auth/login
       │    {email, password}
       ▼
┌──────────────────┐
│  Backend         │
│  - Validates     │
│  - Generates JWT │
└──────┬───────────┘
       │
       │ 2. Returns {token, expiresIn}
       ▼
┌──────────────────┐
│  Frontend        │
│  - Stores JWT    │
│  - localStorage  │
└──────┬───────────┘
       │
       │ 3. Subsequent requests:
       │    Authorization: Bearer {token}
       ▼
┌──────────────────┐
│  Backend         │
│  - Validates JWT │
│  - Extracts user │
└──────────────────┘
```

## 🎯 Key Endpoints Quick Reference

### Public Endpoints (No Auth)
```
POST   /metrics/chat/upload-anonymous
POST   /auth/signup
POST   /auth/login
POST   /auth/verify
GET    /oauth2/authorization/google
GET    /oauth2/authorization/microsoft
```

### Protected Endpoints (Require JWT)
```
POST   /metrics/chat/upload
GET    /metrics/chat/:chatId
POST   /chat-llm/:chatId
GET    /users
GET    /auth/user
```

### Python Microservice Endpoints
```
POST   /analyze              (from backend only)
POST   /rag/upload            (from backend only)
POST   /rag/ask               (from backend only)
GET    /rag/status/:session_id
```

## 💡 Important Notes

1. **Frontend never talks directly to Python** - All communication goes through backend
2. **RAG sessions expire** - 10 minutes TTL, must re-upload to create new session
3. **Anonymous uploads** - Not saved to database, stats only
4. **Authenticated uploads** - Saved to database, can retrieve later
5. **CORS enabled** - Backend allows all origins in development
6. **JWT tokens** - Stored in localStorage, sent in Authorization header
7. **Python is stateless** - Except for RAG cache (in-memory, TTL)



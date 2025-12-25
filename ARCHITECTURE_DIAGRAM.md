# WhatsApp Analytics - System Architecture Diagram

## 🏗️ Complete System Architecture

```mermaid
graph TB
    subgraph "🌐 Frontend Layer"
        FE[React Frontend<br/>localhost:3000<br/>TypeScript + Material-UI]
        FE_ROUTES[Frontend Routes:<br/>- /dashboard<br/>- /upload<br/>- /analytics/:chatId]
    end

    subgraph "🔐 Backend Layer - Spring Boot"
        BE[Spring Boot Backend<br/>localhost:8080<br/>Java 21 + PostgreSQL]
        
        subgraph "Backend Controllers"
            AUTH_CTRL[AuthenticationController<br/>/auth/*]
            METRICS_CTRL[ChatMetricsController<br/>/metrics/*]
            USER_CTRL[UserController<br/>/users/*]
            LLM_CTRL[ChatLLMController<br/>/chat-llm/*]
        end
        
        subgraph "Backend Services"
            AUTH_SVC[AuthenticationService<br/>- Signup/Login<br/>- JWT Generation<br/>- OAuth2]
            METRICS_SVC[ChatMetricsService<br/>- File Processing<br/>- Database Operations]
            LLM_SVC[ChatLLMService<br/>- LLM Integration]
        end
        
        subgraph "Backend Endpoints"
            EP1[POST /auth/signup<br/>POST /auth/login<br/>POST /auth/verify<br/>POST /auth/resend<br/>GET /auth/user]
            EP2[POST /metrics/chat/upload<br/>POST /metrics/chat/upload-anonymous<br/>GET /metrics/chat/:chatId]
            EP3[POST /chat-llm/:chatId]
            EP4[GET /users<br/>GET /users/:id]
        end
    end

    subgraph "🐍 Python Microservice"
        PYTHON[Flask Microservice<br/>localhost:5050<br/>Python + Pandas]
        
        subgraph "Python Endpoints"
            PY_EP1[POST /analyze<br/>- Receives ZIP file<br/>- Parses WhatsApp chat<br/>- Computes analytics<br/>- Returns JSON stats]
            PY_EP2[POST /rag/upload<br/>- Creates RAG session<br/>- Returns session_id]
            PY_EP3[POST /rag/ask<br/>- Answers questions<br/>- Uses OpenAI GPT-4o-mini]
            PY_EP4[GET /rag/status/:session_id]
            PY_EP5[POST /llm/analyze<br/>- Legacy LLM endpoint]
        end
        
        subgraph "Python Processing"
            PARSER[WhatsAppChatParser<br/>- Extracts messages<br/>- Parses timestamps<br/>- Identifies senders]
            ANALYTICS[Analytics Engine<br/>- Message counts<br/>- Weekly volume<br/>- Heatmap data<br/>- Emoji stats<br/>- Streak calculations]
            RAG_CACHE[TTL Cache<br/>- 10 min expiration<br/>- Stores chat context<br/>- 128k token limit]
        end
    end

    subgraph "💾 Database Layer"
        DB[(PostgreSQL Database<br/>- Users<br/>- GroupChats<br/>- ChatMetrics<br/>- JWT tokens)]
    end

    subgraph "🔌 External Services"
        OPENAI[OpenAI API<br/>GPT-4o-mini<br/>- RAG queries<br/>- Chat analysis]
        OAUTH_GOOGLE[Google OAuth2<br/>- User authentication]
        OAUTH_MS[Microsoft OAuth2<br/>- User authentication]
        EMAIL[SMTP Server<br/>Gmail SMTP:587<br/>- Verification emails]
    end

    %% Frontend to Backend connections
    FE -->|HTTP Requests| BE
    FE -->|CORS Enabled| BE
    
    %% Backend internal flow
    BE --> AUTH_CTRL
    BE --> METRICS_CTRL
    BE --> USER_CTRL
    BE --> LLM_CTRL
    
    AUTH_CTRL --> AUTH_SVC
    METRICS_CTRL --> METRICS_SVC
    LLM_CTRL --> LLM_SVC
    
    AUTH_SVC --> DB
    METRICS_SVC --> DB
    METRICS_SVC -->|HTTP POST| PYTHON
    LLM_SVC -->|HTTP POST| PYTHON
    
    %% Python microservice flow
    PYTHON --> PARSER
    PYTHON --> ANALYTICS
    PYTHON --> RAG_CACHE
    RAG_CACHE --> OPENAI
    
    %% External services
    AUTH_SVC --> OAUTH_GOOGLE
    AUTH_SVC --> OAUTH_MS
    AUTH_SVC --> EMAIL
    
    %% Database connections
    AUTH_SVC --> DB
    METRICS_SVC --> DB
    
    style FE fill:#61dafb
    style BE fill:#6db33f
    style PYTHON fill:#3776ab
    style DB fill:#336791
    style OPENAI fill:#10a37f
```

## 📊 Data Flow: Upload → Analysis → Display

```mermaid
sequenceDiagram
    participant User
    participant Frontend as React Frontend<br/>(localhost:3000)
    participant Backend as Spring Boot<br/>(localhost:8080)
    participant Python as Flask Service<br/>(localhost:5050)
    participant DB as PostgreSQL
    participant OpenAI as OpenAI API

    Note over User,OpenAI: Anonymous Upload Flow
    
    User->>Frontend: Upload ZIP file<br/>(via form)
    Frontend->>Backend: POST /metrics/chat/upload-anonymous<br/>MultipartFile (ZIP)
    
    Backend->>Python: POST http://localhost:5050/analyze<br/>MultipartFile (ZIP)
    
    Python->>Python: Extract ZIP file
    Python->>Python: Parse WhatsApp chat<br/>(WhatsAppChatParser)
    Python->>Python: Compute analytics:<br/>- Message counts<br/>- Weekly volume<br/>- Heatmap data<br/>- Emoji stats<br/>- Streaks
    
    Python-->>Backend: JSON Response<br/>{stats, chartData, ...}
    
    Backend-->>Frontend: JSON Response<br/>{stats, chartData, ...}
    
    Frontend->>Frontend: Render Dashboard<br/>- Bar charts<br/>- Line charts<br/>- Heatmaps<br/>- Statistics
    
    Note over User,OpenAI: Authenticated Upload Flow
    
    User->>Frontend: Login (JWT token)
    Frontend->>Backend: POST /auth/login<br/>{email, password}
    Backend->>DB: Verify credentials
    DB-->>Backend: User data
    Backend-->>Frontend: JWT Token
    
    User->>Frontend: Upload ZIP file<br/>(with JWT)
    Frontend->>Backend: POST /metrics/chat/upload<br/>Authorization: Bearer {token}<br/>MultipartFile (ZIP)
    
    Backend->>Python: POST http://localhost:5050/analyze<br/>MultipartFile (ZIP)
    Python-->>Backend: JSON stats
    Backend->>DB: Save GroupChat + ChatMetrics
    DB-->>Backend: Saved chat ID
    Backend-->>Frontend: JSON Response<br/>{stats, chatId, ...}
    
    Note over User,OpenAI: RAG Query Flow
    
    User->>Frontend: Ask question about chat
    Frontend->>Backend: POST /chat-llm/:chatId<br/>{question}
    Backend->>Python: POST /rag/ask<br/>{session_id, question}
    Python->>Python: Retrieve RAG session<br/>(from TTL cache)
    Python->>OpenAI: GPT-4o-mini API call<br/>(with chat context)
    OpenAI-->>Python: AI response
    Python-->>Backend: {answer, ...}
    Backend-->>Frontend: {answer}
    Frontend->>Frontend: Display answer
```

## 🛣️ Complete Route Map

### Frontend Routes (React Router)
```
/                    → Dashboard (main analytics view)
/upload              → File upload page
/analytics/:chatId   → Specific chat analytics
/login               → Login page
/signup              → Registration page
```

### Backend API Routes (Spring Boot - localhost:8080)

#### Authentication (`/auth`)
```
POST   /auth/signup              → Register new user
POST   /auth/login               → Login (returns JWT)
POST   /auth/verify              → Verify email with code
POST   /auth/resend              → Resend verification code
GET    /auth/user                → Get current authenticated user
```

#### Analytics (`/metrics`)
```
POST   /metrics/chat/upload              → Upload chat (authenticated)
POST   /metrics/chat/upload-anonymous    → Upload chat (anonymous)
GET    /metrics/chat/:chatId              → Get metrics for chat
```

#### Chat LLM (`/chat-llm`)
```
POST   /chat-llm/:chatId         → Ask question about chat
```

#### Users (`/users`)
```
GET    /users                    → Get all users (admin)
GET    /users/:id                → Get user by ID
```

#### OAuth2 (Spring Security)
```
GET    /oauth2/authorization/google      → Google OAuth login
GET    /oauth2/authorization/microsoft   → Microsoft OAuth login
GET    /login/oauth2/code/google         → Google callback
GET    /login/oauth2/code/microsoft      → Microsoft callback
```

### Python Microservice Routes (Flask - localhost:5050)

#### Analytics
```
POST   /analyze                  → Main analysis endpoint
       Query params: enable_rag=true/false
       Returns: Full stats + optional session_id
```

#### RAG (Retrieval Augmented Generation)
```
POST   /rag/upload               → Upload chat for RAG
       Returns: {session_id, title, expires_in_seconds}
       
POST   /rag/ask                  → Ask question
       Body: {session_id, question}
       Returns: {answer, messages_used, expires_in_seconds}
       
GET    /rag/status/:session_id   → Check session validity
```

#### Legacy
```
POST   /llm/analyze              → Legacy LLM endpoint
```

## 🔄 Complete Request Flow Example

### Example 1: Anonymous Upload
```
1. User opens browser → http://localhost:3000
2. User uploads ZIP file via frontend form
3. Frontend sends: POST http://localhost:8080/metrics/chat/upload-anonymous
   Headers: Content-Type: multipart/form-data
   Body: file=chat.zip
4. Backend receives file, forwards to Python:
   POST http://localhost:5050/analyze
   Body: file=chat.zip
5. Python extracts ZIP, parses chat, computes stats
6. Python returns JSON with all analytics
7. Backend returns same JSON to frontend
8. Frontend renders charts and statistics
```

### Example 2: Authenticated Upload with RAG
```
1. User logs in → POST /auth/login
   Response: {token: "jwt_token", expiresIn: 3600000}
2. Frontend stores JWT in localStorage
3. User uploads ZIP → POST /metrics/chat/upload
   Headers: Authorization: Bearer {jwt_token}
4. Backend validates JWT, processes file
5. Backend calls Python: POST /analyze?enable_rag=true
6. Python creates RAG session, returns session_id
7. Backend saves to database, returns stats + session_id
8. User asks question → POST /chat-llm/:chatId
   Body: {question: "Who talks more?"}
9. Backend calls Python: POST /rag/ask
   Body: {session_id, question}
10. Python retrieves cached context, calls OpenAI
11. OpenAI returns answer
12. Python returns answer to backend
13. Backend returns answer to frontend
14. Frontend displays answer
```

## 🌐 Network Configuration

### Ports & IPs
```
Frontend:     localhost:3000  (React dev server)
Backend:      localhost:8080  (Spring Boot)
Python:       localhost:5050  (Flask)
Database:     localhost:5432  (PostgreSQL - default)
```

### CORS Configuration
- Backend allows all origins (`*`) in development
- Frontend can make requests to backend
- Python microservice has CORS enabled for all origins

### Security
- JWT tokens for authenticated requests
- OAuth2 for Google/Microsoft login
- Spring Security filter chain
- Anonymous uploads allowed (no auth required)
- Protected endpoints require JWT in Authorization header

## 📦 Data Structures

### Request/Response Examples

#### Upload Request
```http
POST /metrics/chat/upload-anonymous HTTP/1.1
Host: localhost:8080
Content-Type: multipart/form-data

file: [ZIP file bytes]
```

#### Upload Response
```json
{
  "total_messages": 15234,
  "participants_count": 5,
  "top_user": "John Doe",
  "title": "Family Group",
  "messagesPerUser": [...],
  "weeklyVolume": [...],
  "heatmapData": [...],
  "topEmojisOverall": [...],
  "session_id": "uuid-here",
  "expires_in_seconds": 600
}
```

#### RAG Query Request
```http
POST /rag/ask HTTP/1.1
Host: localhost:5050
Content-Type: application/json

{
  "session_id": "uuid-here",
  "question": "Who talks more?"
}
```

#### RAG Query Response
```json
{
  "answer": "Based on the chat messages...",
  "messages_used": 5000,
  "total_messages": 15234,
  "expires_in_seconds": 450
}
```

## 🔍 Key Components Explained

### Frontend (React)
- **Technology**: React 19, TypeScript, Material-UI
- **Purpose**: User interface for uploading chats and viewing analytics
- **Key Files**: 
  - `Dashboard.tsx` - Main analytics dashboard
  - `BarChart.tsx`, `LineChartByWeek.tsx` - Chart components
  - Reads CSV data (legacy) or uses API responses

### Backend (Spring Boot)
- **Technology**: Java 21, Spring Boot 3.5.6, PostgreSQL
- **Purpose**: API gateway, authentication, database operations
- **Key Features**:
  - JWT authentication
  - OAuth2 (Google/Microsoft)
  - File upload handling
  - Database persistence
  - Proxy to Python microservice

### Python Microservice (Flask)
- **Technology**: Python, Flask, Pandas, OpenAI
- **Purpose**: Heavy computation, chat parsing, analytics
- **Key Features**:
  - WhatsApp chat parsing
  - Statistical analysis
  - RAG (Retrieval Augmented Generation)
  - OpenAI integration
  - TTL caching (10 minutes)

### Database (PostgreSQL)
- **Tables**:
  - `users` - User accounts
  - `group_chats` - Chat metadata
  - `chat_metrics` - Analytics data (JSON columns)

## 🚀 How to Run

1. **Start Database**: PostgreSQL on port 5432
2. **Start Backend**: `cd backend && ./gradlew bootRun` (port 8080)
3. **Start Python**: `cd python_microservice && python3 analytics_service.py` (port 5050)
4. **Start Frontend**: `cd chart_js/my_app && npm start` (port 3000)

## 📝 Notes

- Python microservice is stateless (except RAG cache)
- Backend handles all database operations
- Frontend communicates only with backend (never directly with Python)
- RAG sessions expire after 10 minutes
- Anonymous uploads don't save to database
- Authenticated uploads save to database for later retrieval






# API Endpoints

## Authentication API (`/auth`)

| Method | Endpoint       | Request Body | Description | Response |
|--------|----------------|--------------|-------------|----------|
| POST   | `/signup`      | `RegisterUserDto` (`username`, `email`, `password`) | Registers a new user and sends a verification code email. | `User` object (saved user, not yet verified) |
| POST   | `/login`       | `LoginUserDto` (`email`, `password`) | Authenticates user and generates a JWT token. | `LoginResponse` (`token`, `expiresIn`) |
| POST   | `/verify`      | `VerifyUserDto` (`email`, `verificationCode`) | Verifies a user account using the email verification code. | Success message or error |
| POST   | `/resend`      | `String` (email) | Resends verification code to unverified users. | Success message or error |

---

## User API (`/users`)

| Method | Endpoint  | Request Body | Description | Response |
|--------|-----------|--------------|-------------|----------|
| GET    | `/users`  | -            | Returns the currently authenticated user (from JWT). | `User` object |
| GET    | `/users/` | -            | Returns all users (admin-like endpoint). | List of `User` objects |

---

## Analytics API (`/analytics`)

| Method | Endpoint                  | Request Body | Description | Response |
|--------|---------------------------|--------------|-------------|----------|
| POST   | `/analytics/upload-anonymous` | `file` (Multipart `.zip`) | Uploads an anonymous file for analytics processing. | Map of statistics or error message |

---

## Notes on JWT Flow

1. `/auth/login` returns a JWT.
2. Frontend stores the JWT (`localStorage`, `sessionStorage`, or `cookie`).
3. Frontend sends the JWT in the `Authorization: Bearer <token>` header for protected endpoints (`/users`, `/analytics`).
4. Spring Security reads and validates the JWT, populating the `SecurityContext` so controllers can access the authenticated user.

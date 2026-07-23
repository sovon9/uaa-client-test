# uaa-resource-server

A Spring Boot **OAuth2 Resource Server** that demonstrates how to protect REST endpoints using JWT tokens issued by a separate Authorization Server (`auth-service`).

---

## What is a Resource Server?

In the OAuth2 world there are three distinct roles:

| Role | What it does | This project |
|---|---|---|
| **Authorization Server** | Issues JWT tokens after authenticating users/clients | `auth-service` (port 9000) |
| **Resource Server** | Hosts protected data/APIs, validates JWTs | **This service** (port 8080) |
| **Client** | Requests tokens and calls resource APIs | Postman / frontend app |

This service **never issues tokens** — it only **validates** them. It trusts tokens signed by `auth-service` and uses the auth-server's public RSA key to verify signatures.

---

## How the Full Flow Works

```
┌─────────────┐
│   Client    │  (Postman, frontend, another service)
└──────┬──────┘
       │
       │  Step 1 — Get a token
       │  POST http://localhost:9000/oauth2/token
       │  (with client credentials or user login)
       ▼
┌──────────────────┐
│   auth-service   │  Issues a signed JWT containing:
│   (port 9000)    │    - sub (user or client)
│                  │    - scope (e.g. demo.read)
│                  │    - roles (e.g. ROLE_ADMIN)  ← custom claim
│                  │    - iss, exp, iat, jti
└──────┬───────────┘
       │
       │  Step 2 — Call the protected API
       │  GET http://localhost:8080/employee/1
       │  Authorization: Bearer <JWT>
       ▼
┌──────────────────────┐
│  uaa-resource-server │
│  (this service)      │
│  (port 8080)         │
│                      │  a. Extracts JWT from Authorization header
│                      │  b. Fetches auth-server's public key from:
│                      │     http://localhost:9000/oauth2/jwks
│                      │  c. Verifies JWT signature (RSA) + expiry
│                      │  d. Extracts scope + roles → GrantedAuthority
│                      │  e. Allows or denies based on @PreAuthorize
└──────────────────────┘
```

---

## Prerequisites

- **`auth-service` must be running** on `http://localhost:9000` before starting this service.
- This service fetches the public RSA key from `auth-service` at startup via the JWKS endpoint.

---

## Configuration (`application.properties`)

```properties
spring.application.name=uaa-resource-server
server.port=8080

# Points to the Authorization Server.
# Spring automatically fetches the public key from:
#   http://localhost:9000/.well-known/oauth-authorization-server
# and validates all incoming JWT signatures against it.
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9000
```

---

## How JWT Authorities Work

A JWT from `auth-service` looks like this (decoded payload):

```json
{
  "sub": "admin",
  "aud": "demo-app",
  "scope": ["demo.read"],
  "roles": ["ROLE_ADMIN"],
  "iss": "http://localhost:9000",
  "exp": 1784470665
}
```

The `JwtAuthenticationConverter` in `SecurityConfig` maps these claims to Spring Security authorities:

| JWT Claim | Value | Spring Authority | Used with |
|---|---|---|---|
| `scope` | `demo.read` | `SCOPE_demo.read` | `hasAuthority("SCOPE_demo.read")` |
| `roles` | `ROLE_ADMIN` | `ROLE_ADMIN` | `hasRole("ADMIN")` |

> **Note:** `hasRole("ADMIN")` and `hasAuthority("ROLE_ADMIN")` are equivalent — Spring auto-prepends `ROLE_`.

---

## API Endpoints

Base URL: `http://localhost:8080`

All endpoints require a valid `Authorization: Bearer <token>` header.

| Method | Endpoint | Auth Required | Description |
|---|---|---|---|
| `GET` | `/test` | Any valid token | Health check — confirms JWT validation is working |
| `GET` | `/employee/{id}` | `ROLE_ADMIN` | Get employee by ID |
| `POST` | `/employee` | Any valid token | Add a new employee |
| `PUT` | `/employee/{id}` | Any valid token | Update an employee |
| `DELETE` | `/employee/{id}` | Any valid token | Remove an employee |

> **Note:** Data is stored in-memory (no database). Restarting the service resets the employee list to the 3 seeded employees (sovon, sougata, lisha).

---

## How to Test with Postman

### Step 1 — Get a token from auth-service

**Option A — Authorization Code flow** (real user login, includes `roles` in JWT):

1. Open in browser:
   ```
   http://localhost:9000/oauth2/authorize?response_type=code&client_id=demo-app&redirect_uri=http://127.0.0.1:9000/login/authorized&scope=demo.read
   ```
2. Login with `admin` / `changeme`
3. Copy the `code` from the redirect URL
4. Exchange for token — `POST http://localhost:9000/oauth2/token`
   - Authorization: Basic Auth → `demo-app` / `demo-app-secret`
   - Body (form): `grant_type=authorization_code`, `code=<code>`, `redirect_uri=http://127.0.0.1:9000/login/authorized`

**Option B — Client Credentials flow** (machine-to-machine, no user, no `roles` claim):

- `POST http://localhost:9000/oauth2/token`
  - Body (form): `grant_type=client_credentials`, `scope=demo.read`
  - Authorization: Basic Auth → `demo-client` / `demo-secret` (use body params instead of header for this client)

### Step 2 — Call this service

```
GET http://localhost:8080/employee/1
Authorization: Bearer <paste access_token here>
```

---

## Grant Type vs Roles Availability

| Grant Type | Who gets the token | `roles` in JWT? | `@PreAuthorize("hasRole('ADMIN')")` works? |
|---|---|---|---|
| `authorization_code` | Real user (browser login) | ✅ Yes | ✅ Yes |
| `client_credentials` | Machine/service | ❌ No | ❌ No (use scope checks instead) |

---

## Project Structure

```
uaa-resource-server/
├── src/main/java/com/sovon9/uaa_resource_server/
│   ├── UaaResourceServerApplication.java   ← Spring Boot entry point
│   ├── config/
│   │   └── SecurityConfig.java             ← JWT validation + authority mapping
│   └── controller/
│       ├── TestController.java             ← Protected REST endpoints
│       └── Employee.java                   ← Employee model
└── src/main/resources/
    └── application.properties              ← Port + issuer-uri config
```

---

## Key Classes

| Class | Purpose |
|---|---|
| [`SecurityConfig`](src/main/java/com/sovon9/uaa_resource_server/config/SecurityConfig.java) | Configures JWT resource server, authority mapping from JWT claims |
| [`TestController`](src/main/java/com/sovon9/uaa_resource_server/controller/TestController.java) | Demo REST endpoints protected by `@PreAuthorize` |

---

## Common Errors

| Error | Cause | Fix |
|---|---|---|
| `401 Unauthorized` | No token / expired token / wrong issuer | Get a fresh token from auth-service |
| `403 Forbidden` | Token valid but missing required role/scope | Use `authorization_code` flow with admin user to get `ROLE_ADMIN` in token |
| `Connection refused` on startup | `auth-service` not running | Start `auth-service` on port 9000 first |
| `invalid_scope` | Scope in token request doesn't match registered client scopes | Use `demo.read` (dot, not hyphen) |

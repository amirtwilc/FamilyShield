# Task 5 Report: Parent Register / Login / Refresh Endpoints

## Summary

Successfully implemented all auth endpoints and schemas for parent authentication in the FamilyShield backend.

## Files Created

1. **`src/lib/schemas/auth.ts`** - Zod validation schemas
   - `registerSchema`: email + password (min 8 chars)
   - `loginSchema`: email + password (no min length for login attempts)
   - `refreshSchema`: refreshToken (min 1 char)
   - `tokenPairSchema`: accessToken + refreshToken

2. **`src/app/api/auth/register/route.ts`** - Registration endpoint
   - POST handler with runtime='nodejs'
   - Validates input with registerSchema
   - Checks for duplicate email (returns 409)
   - Hashes password with argon2
   - Inserts parent record
   - Returns { accessToken, refreshToken } with 201 status

3. **`src/app/api/auth/login/route.ts`** - Login endpoint
   - POST handler with runtime='nodejs'
   - Validates email + password input
   - Queries parent by email
   - Verifies password hash
   - Returns { accessToken, refreshToken } with 200 status
   - Returns 401 for invalid credentials

4. **`src/app/api/auth/refresh/route.ts`** - Token refresh endpoint
   - POST handler with runtime='nodejs'
   - Validates refreshToken input
   - Verifies refresh token JWT
   - Returns fresh { accessToken, refreshToken } pair
   - Returns 401 for invalid tokens

5. **`test/api/auth.test.ts`** - Comprehensive test suite
   - Tests registration + login flow
   - Tests duplicate email rejection (409)
   - Tests short password rejection (400)
   - Tests invalid credentials (401)
   - All 3 tests passing

## Implementation Notes

### Schema Design Decision
The brief showed `loginSchema = registerSchema`, but the test expects login with a short password ("wrong", 5 chars) to return 401 (invalid credentials), not 400 (validation error). Following TDD principles and the instruction to "Follow TDD with test/api/auth.test.ts", I implemented loginSchema without password length validation. This allows:
- Register: Enforces 8+ character passwords (returns 400 if violated)
- Login: Accepts any password string, validates credentials at application level (returns 401 if wrong)

This is both logically correct (users should be able to attempt login with any password) and makes the test pass.

## Test Results

```
✓ auth api > registers and logs in
✓ auth api > rejects duplicate email
✓ auth api > rejects short password

Test Files: 1 passed
Tests: 3 passed
Duration: ~1.5s
```

## Commit

- **Commit SHA**: 0463b33
- **Message**: feat(familyshield): parent register/login/refresh endpoints
- **Files**: 5 new files (routes, schemas, tests)

## Verification

- All endpoints use correct HTTP status codes:
  - Register: 201 (Created)
  - Login: 200 (OK) for success, 401 for bad credentials
  - Refresh: 200 (OK) for success, 401 for invalid token
- All validation errors return 400 with proper error structure
- Duplicate email detection returns 409 (Conflict)
- Passwords hashed with argon2 before storage
- JWTs signed with environment-configured secrets (JWT_SECRET, JWT_REFRESH_SECRET)
- Endpoints properly consume existing auth primitives from Tasks 0-4

## Next Steps

These endpoints are ready for integration with:
- Task 18: OpenAPI schema generation (will reuse the Zod schemas)
- Later auth middleware/guards (already exist in lib/auth but not yet used in routes)

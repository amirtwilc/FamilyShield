# Task 4 Report: Auth primitives (password, JWT, device token)

## Status: COMPLETED

### Summary
Successfully implemented Task 4: Auth primitives for FamilyShield backend using TDD methodology.

### Implementation Details

#### Step 1: Verified Existing Code
- Confirmed `src/lib/auth/device.ts` already exists with required functions:
  - `hashToken(token: string): string` - SHA256 hex hash
  - `createDeviceToken(): { token: string; hash: string }` - random device token generation
- No modifications were needed to this file

#### Step 2: TDD Process
1. **Created test file** (`test/lib/auth.test.ts`):
   - 3 test cases covering password hashing/verification, JWT sign/verify, and device token determinism
   - Test initially failed as expected (modules missing)

2. **Implemented password.ts** (`src/lib/auth/password.ts`):
   - `hashPassword(pw: string): Promise<string>` - Uses argon2.hash
   - `verifyPassword(hash: string, pw: string): Promise<boolean>` - Uses argon2.verify with error fallback
   - Dependencies: argon2 (already installed in Task 0)

3. **Implemented jwt.ts** (`src/lib/auth/jwt.ts`):
   - `signAccess(parentId: string): Promise<string>` - 15-minute expiration
   - `signRefresh(parentId: string): Promise<string>` - 30-day expiration
   - `verifyAccess(token: string): Promise<string>` - Returns parentId, throws on invalid
   - `verifyRefresh(token: string): Promise<string>` - Returns parentId, throws on invalid
   - Uses jose library (SignJWT, jwtVerify) with HS256 algorithm
   - Reads JWT_SECRET and JWT_REFRESH_SECRET from environment
   - Dependencies: jose (already installed in Task 0)

#### Step 3: Test Results
All 3 tests pass:
- ✓ hashes and verifies passwords (337ms)
- ✓ signs and verifies access tokens
- ✓ device token hash is deterministic and matches

### Files Created
- `src/lib/auth/password.ts` (6 lines)
- `src/lib/auth/jwt.ts` (25 lines)
- `test/lib/auth.test.ts` (24 lines)

### Files Unchanged
- `src/lib/auth/device.ts` (intentionally not staged, as it already existed with correct implementation)

### Commit Details
- **SHA**: 5d7dc14
- **Message**: "feat(familyshield): password, jwt, device-token primitives"
- **Files staged**: 3 (password.ts, jwt.ts, auth.test.ts)
- **Co-authored-by**: Claude Opus 4.8 <noreply@anthropic.com>

### Environment Configuration
- JWT_SECRET and JWT_REFRESH_SECRET are read from `.env`
- Dotenv/config is configured in Vitest (via Task 2 setup)
- argon2 and jose dependencies are already installed

### Next Steps
Task 4b requires implementing auth guards:
- `src/lib/auth/parent.ts` - `requireParent(req)` guard
- Modify `src/lib/auth/device.ts` - Add `requireDevice(req)` guard
- Create `test/lib/guards.test.ts` with guard tests

These will depend on this task's exports (verifyAccess, hashToken).

### Testing Notes
- Ran `npx vitest run test/lib/auth.test.ts` - all 3 tests pass
- No existing tests were broken
- .env file was not committed (as per requirements)

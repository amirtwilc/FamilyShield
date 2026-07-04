# Task 4b Report: Auth Guards (requireParent / requireDevice)

## Summary
Successfully implemented the two HTTP auth guards for the FamilyShield backend. Both guards verify bearer tokens and return either an authenticated entity or a 401 error response.

## Implementation Details

### Files Created
- **`src/lib/auth/parent.ts`** — New file implementing `requireParent(req)` guard
  - Extracts Bearer token from request headers
  - Verifies JWT using existing `verifyAccess()` function
  - Returns `{ parentId: string }` on success or `{ response: Response }` with 401 status on failure

### Files Modified
- **`src/lib/auth/device.ts`** — Appended `requireDevice(req)` guard
  - Preserved existing `hashToken()` and `createDeviceToken()` crypto functions
  - Added Drizzle ORM imports and database client
  - Extracts Bearer token from request headers
  - Queries devices table for non-revoked device (revokedAt IS NULL)
  - Returns `{ device: typeof devices.$inferSelect }` on success or `{ response: Response }` with 401 status on failure

### Files Created
- **`test/lib/guards.test.ts`** — Test suite with 3 tests
  - Tests requireParent with valid token → returns parentId
  - Tests requireParent without token → returns 401 response
  - Tests requireDevice with valid token → returns device object

## Test Results
All 3 tests passed:
- ✓ test/lib/guards.test.ts (3 tests) — 810ms
- Test Files: 1 passed
- Tests: 3 passed

## Key Implementation Patterns
1. Both guards use consistent bearer token extraction pattern
2. Both guards return discriminated union types for type-safe error handling
3. requireDevice checks `revokedAt IS NULL` to reject revoked devices
4. Uses existing infrastructure: `verifyAccess()` for JWT validation, seed factories for testing

## Commit
- **SHA**: `127f676`
- **Subject**: `feat(familyshield): parent + device auth guards`
- **Files**: 
  - `src/lib/auth/parent.ts` (created)
  - `src/lib/auth/device.ts` (modified)
  - `test/lib/guards.test.ts` (created)

## Notes
- All exports follow task specification exactly
- No existing code removed or rewritten — device.ts extensions are purely additive
- Uses live PostGIS DB for integration testing via resetDb() and seed factories
- Both guards follow established error response pattern from http utility

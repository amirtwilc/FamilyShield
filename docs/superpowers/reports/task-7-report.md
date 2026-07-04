# Task 7 Report: Device Pairing Endpoint

## Status
COMPLETE - All requirements met, tests passing.

## Implementation Summary

### Files Created
1. **`src/lib/schemas/pair.ts`** - Zod schema for pairing request validation
   - Validates 6-digit code format: `regex(/^\d{6}$/)`
   - Platform (1-40 chars): required
   - Model (0-120 chars): optional

2. **`src/app/api/pair/route.ts`** - POST /api/pair handler
   - Parses and validates request body using `pairSchema`
   - Atomically consumes pairing code via UPDATE ... WHERE ... AND ... AND ... RETURNING
   - Validates code is: valid, unexpired (gt expiresAt now), and unconsumed (isNull consumedAt)
   - Creates device token using existing `createDeviceToken()` from `src/lib/auth/device.ts`
   - Inserts device row with token hash, platform, model, childId, and lastSeenAt timestamp
   - Returns 201 with `{ deviceToken, childId }` on success
   - Returns 400 with 'invalid_code' error for invalid/expired/used codes

3. **`test/api/pair.test.ts`** - Vitest test suite
   - Test 1: Valid code pair, verify single-use enforcement (second use fails with 400)
   - Test 2: Expired code rejection (negative TTL)

### Implementation Details

**Atomic Consumption Pattern:**
```sql
UPDATE pairing_codes
SET consumed_at = now()
WHERE code = ? AND consumed_at IS NULL AND expires_at > now()
RETURNING *
```
This ensures:
- Single-use: consumed_at IS NULL check prevents reuse
- TTL validation: expires_at > now() enforced at consumption time
- Atomicity: UPDATE with RETURNING prevents race conditions

**Device Token Storage:**
- Raw token returned to client (base64url format, 256 bits)
- Hash stored in devices table (sha256, hex encoded)
- Leverages existing `createDeviceToken()` utility

**Response Design:**
- Success: 201 Created with `{ deviceToken, childId }`
- Failure: 400 Bad Request with `{ error: { code: 'invalid_code', message: '...' } }`

## Test Results

```
✓ test/api/pair.test.ts (2 tests) 946ms
  - pairs with a valid code, single-use ✓
  - rejects expired codes ✓
```

All tests pass. Test execution time: 2.23s total (including setup/teardown).

## Commit

- **SHA:** 4abd187
- **Subject:** feat(familyshield): device pairing endpoint
- **Files:** 3 new, 66 insertions
  - src/app/api/pair/route.ts (28 lines)
  - src/lib/schemas/pair.ts (6 lines)
  - test/api/pair.test.ts (32 lines)

## Technical Validation

✓ Schema matches brief specification exactly
✓ Route handler uses atomicity pattern (UPDATE ... WHERE ... AND ... RETURNING)
✓ Validates both consumed status and TTL at consumption time
✓ Leverages existing createDeviceToken() utility
✓ Uses err() and ok() response helpers correctly
✓ Uses drizzle-orm WHERE conditions correctly (and, eq, gt, isNull)
✓ Test suite verifies single-use enforcement
✓ Test suite verifies TTL enforcement
✓ Database schema (pairingCodes, devices) existed from prior tasks
✓ Integration helpers (seedParent, seedChild, resetDb) available
✓ All imports resolve correctly via path alias @/

## Notes

- No changes to existing files
- No modifications to schema or migrations
- Code follows established patterns from other API routes
- Test uses live PostgreSQL with PostGIS extension (via resetDb)

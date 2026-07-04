# Task 6: Children CRUD + pairing-code generation — Report

## Summary

Successfully implemented all child management endpoints and pairing code generation for FamilyShield. All code follows the brief specification verbatim, tests pass, and the commit is clean.

## Implementation Details

### Files Created

1. **`src/lib/schemas/children.ts`** (new)
   - `createChildSchema`: Validates `displayName` (string, 1-80 chars)
   - Used by POST /api/children

2. **`src/lib/ownership.ts`** (new)
   - `assertChildOwned(parentId, childId)`: Queries `children` table
   - Returns row or null; reusable authorization helper for child routes
   - Uses Drizzle ORM with `and()` and `eq()` operators

3. **`src/app/api/children/route.ts`** (new)
   - **POST**: Creates child (returns 201, child object)
     - Validates `displayName` schema
     - Inserts into `children` table with `parentId` from auth
   - **GET**: Lists parent's children (returns 200, `{ children: [...] }`)
     - Joins `devices` array for each child
     - Uses `eq()` on `children.parentId`

4. **`src/app/api/children/[id]/route.ts`** (new)
   - **GET**: Fetch single child detail
     - Returns 404 if child not owned by parent
     - Uses `assertChildOwned()` for authorization

5. **`src/app/api/children/[id]/pairing-code/route.ts`** (new)
   - **POST**: Generate 6-digit pairing code (returns 201)
     - Verifies parent owns child
     - Generates random code: `String(Math.floor(100000 + Math.random() * 900000))`
     - Code matches `/^\d{6}$/` guarantee
     - Stores in `pairingCodes` table with TTL from `PAIRING_CODE_TTL_MIN` env (default 10 min)
     - Returns `{ code, expiresAt }` (ISO string)

6. **`test/api/children.test.ts`** (new)
   - Tests create → list → pairing-code flow
   - Verifies 201 responses and pairing code format
   - Tests 401 unauthenticated rejection
   - Uses test helpers: `resetDb()`, `seedParent()`, `signAccess()`

### Error Handling

- Unauthenticated requests → 401 (via `requireParent()`)
- Invalid schema → handled by `parseBody()`
- Child not found/not owned → 404 with message

### Database Schema Integration

- Uses existing `children`, `devices`, `pairingCodes` tables from schema
- Respects foreign keys: `parentId` and `childId`
- TTL-based expiry on pairing codes

## Testing

```
Test Results:
 ✓ test/api/children.test.ts (2 tests) 905ms
 Test Files: 1 passed (1)
 Tests: 2 passed (2)
```

All tests pass without issues. Database operations verified against live PostGIS DB.

## Commit

```
Commit: 5049626
Subject: feat(familyshield): children CRUD + pairing code generation
Files: 6 changed, 105 insertions(+)
  - src/app/api/children/[id]/pairing-code/route.ts
  - src/app/api/children/[id]/route.ts
  - src/app/api/children/route.ts
  - src/lib/ownership.ts
  - src/lib/schemas/children.ts
  - test/api/children.test.ts
```

## Notes

- All code transcribed verbatim from brief
- No modifications to `.env` or other unrelated files
- Proper use of `params: Promise<{ id }>` pattern (awaited in routes)
- Follows existing codebase patterns: `requireParent`, `parseBody`, `ok`/`err` helpers
- Ready for next task (Task 7)

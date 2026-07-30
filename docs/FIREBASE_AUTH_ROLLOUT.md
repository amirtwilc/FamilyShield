# Firebase parent-authentication rollout

FamilyShield uses Firebase Authentication for parent credentials and Neon for
application data. Kid-device bearer tokens are unchanged.

## 1. Configure the existing Firebase/FCM project

In the Firebase console:

1. Enable Email/Password and Google providers.
2. Keep "one account per email address" enabled.
3. Enable email-enumeration protection.
4. Configure the password policy:
   - minimum 8 characters;
   - maximum 128 characters;
   - uppercase, lowercase, numeric, and non-alphanumeric characters required.
5. Configure the Firebase-hosted verification and password-reset templates.
   The Android app sets the email language to English or Hebrew from the device
   locale before sending either message.
6. Register the Android app and place its production configuration at
   `android/app/google-services.json`.
7. Register the production Android signing certificate and enable Play Integrity
   under App Check. Enforce App Check for Authentication after rollout testing.

Debug APKs use Firebase's debug App Check provider. Register the debug token
printed to Logcat in the Firebase console before testing protected migration
calls locally. Release APKs contain only the Play Integrity provider.

Set the backend variables:

```text
FIREBASE_PROJECT_ID=<the existing FCM project id>
FIREBASE_SERVICE_ACCOUNT_JSON=<service-account JSON or base64 JSON>
FIREBASE_REQUIRE_APP_CHECK=true
LEGACY_AUTH_CUTOFF_AT=<UTC timestamp exactly 30 days after dual-auth deployment>
```

The old `FCM_SERVICE_ACCOUNT_JSON` name remains a temporary fallback. Use one
service account/project for Authentication and Cloud Messaging.

## 2. Audit before changing Neon

Run the read-only preflight first:

```powershell
npm run auth:preflight
```

Use `node scripts/check-firebase-auth-migration.mjs --database-only` if Firebase
credentials are not available yet. Resolve every duplicate normalized email,
Firebase UID/email conflict, and Google-subject mismatch before proceeding.

## 3. Add the mapping columns

Apply `drizzle/0019_firebase_parent_auth.sql`:

```powershell
npm run db:prod:setup
```

The migration lowercases and trims parent emails, adds case-insensitive
uniqueness, and adds the nullable unique `firebase_uid` and
`auth_migrated_at` columns.

## 4. Pre-provision users

The provisioning command is dry-run-only by default:

```powershell
npm run auth:provision
npm run auth:provision:apply
```

Each Firebase UID is the existing Neon parent UUID. Password hashes are not
imported because the legacy Argon2 parameters are incompatible with Firebase's
Node import path. A legacy Google subject is imported as `google.com` provider
data and is the only reason a pre-provisioned email is initially marked verified.

The command aborts on any conflict and is safe to rerun.

## 5. Deploy and retire legacy authentication

Deploy the dual-auth backend, then release the Firebase-configured Android APK.
The old registration, login, refresh, Google, and JWT authorization paths stop
working at `LEGACY_AUTH_CUTOFF_AT`. The App Check-protected
`/api/auth/legacy-migrate` endpoint remains available so a late password user can
migrate once.

Monitor:

```sql
SELECT
  count(*) FILTER (WHERE firebase_uid IS NULL) AS not_provisioned,
  count(*) FILTER (WHERE password_hash IS NOT NULL) AS legacy_passwords,
  count(*) FILTER (WHERE google_sub IS NOT NULL) AS legacy_google_subjects
FROM parents;
```

Do not add or apply `0020_remove_legacy_parent_auth.sql` while any of these
counts are non-zero. The production migration runner applies every SQL file, so
the destructive cleanup migration is intentionally not present yet. Once all
identities have migrated, create `0020` to remove `password_hash`, `google_sub`,
the legacy routes/JWT code, Argon2, and `JWT_SECRET`/`JWT_REFRESH_SECRET`.

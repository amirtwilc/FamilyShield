# Google ("Gmail") sign-in setup

The code is implemented; it needs **your** Google OAuth credentials to work.
Parents can then tap **Continue with Google** on the login screen, and the backend
verifies the Google ID token and issues the normal session tokens (creating or
linking a parent by email).

## 1. Create OAuth credentials (Google Cloud Console)
**APIs & Services → Credentials → Create credentials → OAuth client ID.**

1. **Web application** client → copy its **Client ID** (`xxxx.apps.googleusercontent.com`).
   This single Web client id is used by **both** the backend (token audience) and the
   Android app (`serverClientId`).
2. **Android** client → set package name `com.familyshield.app` and the **SHA-1** of
   your signing certificate (`keytool -list -v -keystore <your.keystore>`; for debug,
   `~/.android/debug.keystore`, password `android`). This registers the app so Google
   will issue it ID tokens. (No value goes in code — registration is enough.)
3. **OAuth consent screen**: External, add scopes `email` + `profile`, and add your
   Google account under **Test users** (until you publish).

## 2. Configure the backend
Set on the server (Railway variables / `.env` / docker-compose):
```
GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com   # the WEB client id
```
Blank = `/api/auth/google` is disabled (fails closed). The route verifies the token's
issuer, signature (Google JWKS), and audience == `GOOGLE_CLIENT_ID`.

## 3. Build the Android app with the client id
```
cd android
./gradlew :app:assembleDebug   -PAPI_HOST=<lan-ip>   -PGOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com
# or release:
./gradlew :app:assembleRelease -PAPI_BASE=https://<domain> -PGOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com
```
A blank `GOOGLE_CLIENT_ID` hides the button. The device needs **Google Play Services**
and a Google account signed in (use a Google-APIs emulator image or a real phone).

## How it behaves
- New Google email → a passwordless parent is created.
- Google email that matches an existing email/password account → Google is linked to it
  (both sign-in methods then work).
- Returning user → matched by Google subject id.

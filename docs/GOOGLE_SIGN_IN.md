# Google sign-in at launch

The app opens on a sign-in page until someone is signed in with Google. The session is kept by Firebase
Authentication, so the page comes back only after **Sign out** (main menu).

## How it works

`MainActivity` asks `AuthGate` first. If sign-in is required it opens `LoginActivity` with the launch intent and
finishes, so nothing else starts (and a project file opened with the app is still imported after signing in).
`LoginActivity` shows the system's Google account sheet through Credential Manager, exchanges the Google ID token
for a Firebase session, then opens the app.

A build without Firebase configuration (a fork or CI build without the `GOOGLE_SERVICES_JSON` secret) can't sign
anyone in, so it opens normally instead of locking the app (`AuthDecision.NOT_CONFIGURED`).

## What the Firebase project needs

1. **Authentication > Sign-in method > Google** enabled.
2. The SHA-1 of **every key that signs a build** added under Project settings > Your apps > `com.sketch_nws`:
   - the public test key (debug builds and CI test APKs): `61:ED:37:7E:85:D3:86:A8:DF:EE:6B:86:4B:D8:5B:0B:FA:A5:AF:81`
   - the release key: `keytool -list -v -keystore <release.jks>` (SHA1 line). It is a CI secret, so it can't be read from the repo.
3. The `google-services.json` downloaded **after** step 2. Before, it only holds the web client (`client_type: 3`);
   with the SHA-1 registered it also holds an Android client (`client_type: 1` with a `certificate_hash`). Without that
   Android client, Google rejects the sign-in with a developer error.

`google-services.json` is gitignored and reaches the build through the `GOOGLE_SERVICES_JSON` secret: update that
secret, don't commit the file.

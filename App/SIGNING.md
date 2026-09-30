# Puppy Clicker Android signing

Puppy Clicker keeps the Android application ID `com.harleytg.puppyclicker`.

## Production model: Google Play App Signing

Google Play App Signing is authoritative for production installs. Puppy Clicker CI signs the Android App Bundle (AAB) with the **upload key** only. Google Play verifies that upload signature, then signs the APKs delivered to users with Google-held app-signing keys.

The public `.der` certificates exported from Play Console are verification certificates only. They do not contain private keys and cannot sign an APK or AAB.

### Expected upload-key certificate

The configured upload keystore must resolve to this SHA-256 certificate fingerprint:

`FF:BF:F1:C6:C6:CC:96:EC:8E:4C:56:73:20:27:C9:A8:C0:09:47:8B:E2:E9:14:E5:55:C9:CE:5A:07:19:34:B8`

CI checks this fingerprint before building a production upload bundle. A mismatch fails the workflow instead of producing a bundle with the wrong upload identity.

### Google Play delivery certificates

The Play Console certificate export supplied on 2026-09-30 contains:

- Deployment certificate — RSA 4096 — SHA-256 `63:E3:F1:5F:39:69:5B:74:F9:3B:A8:3F:27:51:86:FC:3D:45:FB:A9:5A:9C:7E:27:E1:E5:AA:14:55:51:25:EC`
- Quantum-ready hybrid classical certificate — RSA 4096 — SHA-256 `67:41:63:6C:29:75:29:66:9C:1E:1A:1A:08:59:18:67:A8:4F:AD:76:4B:1C:9D:9E:BB:DB:19:31:5A:17:5B:C9`
- Quantum-ready hybrid PQC certificate — ML-DSA-65 — SHA-256 `99:23:02:B9:01:AE:D7:44:49:E2:46:B4:00:4B:95:1B:19:14:16:34:84:63:54:5C:24:8E:EB:3F:A9:2A:88:AD`

Register all Play delivery fingerprints with any external service that authenticates the installed Android app by certificate fingerprint. Do not substitute the upload-key fingerprint for the Google Play delivery fingerprints in those services.

## GitHub Actions secrets

Preferred Play-upload secrets:

- `PUPPY_UPLOAD_KEYSTORE_B64` — base64 encoding of the upload-key PKCS12/JKS keystore
- `PUPPY_UPLOAD_STORE_PASSWORD` — keystore password
- `PUPPY_UPLOAD_KEY_ALIAS` — upload key alias
- `PUPPY_UPLOAD_KEY_PASSWORD` — private-key password

For migration only, the workflow also accepts the previous `PUPPY_KEYSTORE_B64` / `PUPPY_SIGNING_*` secret names. The fingerprint gate still requires the configured key to match the expected upload certificate above.

Never commit a private upload keystore or password. The Google Play app-signing private keys are not available to this repository and should not be requested or recreated.

## Release artifact

Production:

```bash
cd App
gradle --no-daemon :app:bundleRelease --stacktrace
```

The production artifact is:

`App/app/build/outputs/bundle/release/app-release.aab`

Upload that signed AAB to Google Play. Google Play generates and signs the installable APK splits.

A locally or GitHub-signed APK is **not** a production update path for a Play-installed copy of Puppy Clicker. The in-app update action therefore routes users to the Google Play listing rather than downloading a GitHub APK.

## Previous self-managed signing identity

Older non-Play builds documented the self-managed certificate:

`60:1D:79:42:41:5D:3A:9F:B4:FE:F2:0D:D1:EC:E5:99:FC:9D:E9:5E:B7:F2:70:AF:C4:71:2A:0E:7A:81:6C:7B`

That identity is retained here only for migration/history. An installation signed only with that legacy certificate cannot automatically accept an APK signed by an unrelated Google Play key unless Android has a valid signing-key upgrade lineage covering the transition. If no such lineage exists, that legacy installation requires a one-time uninstall/reinstall from Google Play.

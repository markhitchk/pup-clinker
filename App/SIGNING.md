# Puppy Clicker Android update signing

Puppy Clicker keeps the Android application ID `com.harleytg.puppyclicker` for every release.

Starting with Android version `1.2.0` (`versionCode 3`), release APKs use one permanent signing identity. Future APK updates must keep this application ID, use a strictly higher `versionCode`, and be signed with the same permanent key.

## Permanent signing certificate

- Key type: RSA 4096-bit
- Certificate SHA-256: `60:1D:79:42:41:5D:3A:9F:B4:FE:F2:0D:D1:EC:E5:99:FC:9D:E9:5E:B7:F2:70:AF:C4:71:2A:0E:7A:81:6C:7B`
- Key alias: `puppyclicker`
- APK Signature Schemes: v1, v2, v3 and v4 enabled by the Android signing configuration. v4 is emitted as the separate `.idsig` sidecar.

Never regenerate, replace, or commit the private signing key. Losing the private key means future builds cannot update existing installations signed with it.

## GitHub Actions secrets

The workflow creates an updateable release only when all four secrets below are configured:

- `PUPPY_KEYSTORE_B64` — base64 encoding of the PKCS12 signing keystore
- `PUPPY_SIGNING_STORE_PASSWORD` — PKCS12 store password
- `PUPPY_SIGNING_KEY_ALIAS` — `puppyclicker`
- `PUPPY_SIGNING_KEY_PASSWORD` — private-key password

If none of these secrets are present, CI performs a debug compile test only and deliberately does not publish an APK artifact. If only some are present, the workflow fails to prevent accidentally changing the signing identity.

## Transition from the early debug builds

Early development APKs were signed by temporary GitHub runner debug keys. Those temporary private keys were not persistent, so Android cannot accept a permanently signed release as an in-place update to those particular installs.

There is therefore one required transition: uninstall the old temporary-debug-signed Puppy Clicker APK once, install the permanent-signed `1.2.0` build, and then keep using permanent-signed releases. From `1.2.0` onward, normal Android in-place updates are supported as long as the package ID, permanent signing key, and increasing `versionCode` are preserved.

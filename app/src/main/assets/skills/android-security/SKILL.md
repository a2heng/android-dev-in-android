---
name: Android Security
description: Apply Android security basics: least-privilege permissions, exported components, network security, keystore/secrets, WebView and backup rules. Use before shipping or when reviewing security-sensitive code.
---

# Android Security

## Permissions

- Request only what the feature uses. Prefer runtime permissions at the point
  of use.
- Avoid `MANAGE_EXTERNAL_STORAGE` unless the app is a file manager/IDE; it
  triggers store review and user distrust.
- `REQUEST_INSTALL_PACKAGES` is high risk; this project needs it for
  on-device builds, but explain why.

## Components

- Every activity/service/receiver with an intent filter must set
  `android:exported` explicitly.
- Do not export components that do not need to be public.
- Validate all data received from external intents/URIs.
- Use `PendingIntent.FLAG_IMMUTABLE` unless mutability is required.

## Network

`res/xml/network_security_config.xml`:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```

Reference it from the manifest:

```xml
<application android:networkSecurityConfig="@xml/network_security_config" ...>
```

Do not disable certificate validation. Do not log tokens or Authorization
headers.

## Secrets

- Never hard-code API keys in Java/Kotlin or `BuildConfig`.
- Use `EncryptedSharedPreferences` or the Android Keystore for local secrets.
- Keep provider tokens in the opencode config, not in the app source.
- Redact `sk-`, `ghp_`, `Bearer` when sharing logs.

## WebView

- Disable JavaScript unless required; if required, restrict navigation to
  trusted hosts.
- Do not add `addJavascriptInterface` for untrusted content.
- Set `setAllowFileAccess(false)`, `setAllowContentAccess(false)` when possible.
- This launcher uses a local WebView for opencode; keep it bound to
  `127.0.0.1` and authenticated.

## Backup and debugging

- `android:allowBackup="false"` for apps with tokens/sessions.
- Remove `android:debuggable` from release builds.
- Keep `android:usesCleartextTraffic` off unless a local loopback server needs
  it; scope it if possible.

## Review checklist

1. Permissions match features.
2. Exported components minimized and validated.
3. No secrets in source or logs.
4. Network config denies cleartext.
5. Release build has R8 and no debug flags.

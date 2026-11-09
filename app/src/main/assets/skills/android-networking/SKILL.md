---
name: Android Networking
description: Add HTTP/API calls to an Android app: OkHttp/Retrofit/Ktor, coroutines, timeouts, retries, caching and safe error handling. Use when integrating a backend or downloading data.
---

# Android Networking

## Dependencies

Prefer the smallest stack that fits:

```groovy
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
// or
implementation 'com.squareup.retrofit2:retrofit:2.11.0'
implementation 'com.squareup.retrofit2:converter-gson:2.11.0'
```

Check the offline cache first (`android-deps`); these may not be bundled.

## Rules

- Never do network I/O on the main thread.
- Use `suspend` functions and `Dispatchers.IO`.
- Set connect/read/call timeouts explicitly.
- Retry only idempotent requests, with backoff and a cap.
- Handle HTTP errors and malformed JSON; don't crash on empty bodies.
- Do not log tokens or full Authorization headers.
- Use HTTPS; do not disable certificate validation.
- Cache only what is safe; respect `Cache-Control`.

## Minimal OkHttp example

```kotlin
val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

suspend fun get(url: String): String = withContext(Dispatchers.IO) {
    client.newCall(Request.Builder().url(url).build()).execute().use { r ->
        if (!r.isSuccessful) error("HTTP ${r.code}")
        r.body?.string().orEmpty()
    }
}
```

## Android manifest

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Add `ACCESS_NETWORK_STATE` only if you actually inspect connectivity.

## Test on the phone

- The phone may be on a hotspot or mobile data; check `adb shell dumpsys connectivity`.
- Local loopback (`127.0.0.1`) does not need cleartext permission on most
  versions, but a `networkSecurityConfig` may still block it; scope exceptions
  to `localhost` only.
- Test offline/airplane mode and slow networks, not only Wi-Fi.

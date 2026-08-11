# Viora local-data guarantee

Viora is designed without an application backend. The installed app connects directly to the configured official VTOP HTTPS origin and nowhere else for academic synchronization.

## Data that stays on the device

- VTOP credentials when the user enables remembered login
- Viora's private VTOP cookies and CSRF state
- Profile and academic records
- Parser input and normalized records
- Attendance projections and timeline calculations
- Notification preferences and history
- Downloaded course material

App data is held in Android's private application sandbox, excluded from cloud backup and device transfer, and removed by Viora's local logout flow. Credentials and VTOP cookies receive an additional AES-GCM encryption layer backed by an app-specific Android Keystore key.

## Session isolation

Viora owns a private OkHttp cookie jar. It neither reads nor writes Chrome, Android WebView, the official VIT app, or another device's cookies. “Log out of Viora” means local deletion and does not send a request to VTOP's logout endpoint. VTOP may still impose server-side session policies outside Viora's control.

## Excluded services

There is no Viora cloud account, proxy, sync server, analytics SDK, advertising SDK, remote CAPTCHA solver, or crash-report upload containing academic data.

Release automation publishes only the signed APK and its SHA-256 checksum. Normal CI does not upload APKs, databases, logs, screenshots, UI dumps, HTML responses, or test reports. Device-smoke output must remain private and is excluded from Git.

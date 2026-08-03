# VTOP Companion

An offline-first Android academic dashboard for VIT students. Sign in during setup, then open the app to see a locally cached dashboard while a fresh VTOP sync runs in the background.

> This is an independent student project. It is not affiliated with or endorsed by VIT. It must not bypass CAPTCHA, access controls, or VTOP usage policies.

## Product promise

- One setup login, with explicit re-authentication only when VTOP invalidates the session.
- A useful home screen immediately, even on weak campus Wi-Fi.
- One academic timeline combining classes, DAs, assessments, exams, holidays, and class messages.
- Explainable attendance calculations: never label a class “safe to skip” without showing the assumptions.
- Credentials and session material remain on the device.

## Status

This repository starts with the product and technical design. See:

- [Architecture](docs/ARCHITECTURE.md)
- [Feature plan](docs/FEATURES.md)
- [Sync and login](docs/SYNC_AND_AUTH.md)
- [Delivery roadmap](docs/ROADMAP.md)
- [Contributing and checkpoint pushes](CONTRIBUTING.md)

## Proposed stack

Kotlin, Jetpack Compose, Material 3, Navigation, ViewModel + StateFlow, Hilt, Room, DataStore, OkHttp, Kotlinx Serialization, WorkManager, Android Keystore, and baseline/profile-guided performance tooling.

The VTOP integration is isolated behind a `VtopGateway` contract because VTOP is an HTML application, not a stable public API. Parser fixtures and contract tests are mandatory before shipping.

## Repository policy

Never commit VTOP usernames, passwords, cookies, CAPTCHA images, registration numbers, downloaded course material, or captured HTML containing student data. `.gitignore` includes common local secret and capture paths.


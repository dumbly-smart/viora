# Viora

Viora is an Android app for keeping VTOP stuff in one place without opening ten different pages every time.

Log in once during setup and Viora keeps your academic data synced whenever you open the app. Everything stays on your phone, so your cached timetable and other details are still there when campus Wi-Fi decides not to cooperate.

## What it has right now

- Today’s classes and the next class coming up
- Full weekly timetable
- Shareable timetable QR image generated entirely on-device
- Attendance for theory and lab separately
- Skippable-class and recovery projections at the 75% target
- Attendance what-if planning with adjustable targets and whole lab blocks
- Digital assignments and due dates
- Exam schedule, venue, and seat details
- Assessment marks and weighted marks
- Grades, GPA, CGPA, and credits
- Academic calendar and holidays
- Class messages
- Consolidated course details
- Course-material download, open, and share actions
- Managed downloads with retry state, private-storage usage, and cleanup
- Local reminders for assignments and exams
- Change alerts, quiet hours, notification deep links, and fully local search
- Dedicated course, assignment, exam, and material details
- Semester rollover archives and responsive phone/tablet navigation
- Accessibility-aware headings, live error announcements, and large-text layouts
- Background sync with cached data when VTOP is unavailable
- A seven-day timeline that accounts for holidays and instructional day orders

## Login and privacy

Viora talks directly to VTOP and does not use a backend. Credentials, cookies, marks, attendance, and everything else are stored locally on the device.

The app has its own encrypted VTOP session, separate from Chrome and other devices. Logging out of Viora only clears Viora’s local data, so it should not mess with another VTOP session you already have open.

If VTOP expires the session, Viora restores it using the encrypted login saved during setup. If VTOP specifically asks for verification, the app will need you to complete that step normally—there is no CAPTCHA-solving service involved.

## Tech stuff

The app is built with:

- Kotlin and Jetpack Compose
- Material 3
- Room
- ViewModel and StateFlow
- OkHttp and Jsoup
- WorkManager
- Android Keystore

The basic flow is pretty simple:

```text
Compose UI → ViewModel → Repository → Room
                         ↓
                    VTOP gateway
```

Room is the source of truth for the UI. A sync updates the local database, and the screens automatically pick up the new data.

## Running it

Open the project in Android Studio with JDK 17 and let Gradle sync. Then run the `app` configuration on an Android 8.0 or newer device/emulator.

From the command line:

```bash
gradle testDebugUnitTest lintDebug
gradle assembleDebug
```

## Project notes

- [Detailed tech stack](docs/TECH_STACK.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Features](docs/FEATURES.md)
- [Sync and login](docs/SYNC_AND_AUTH.md)
- [Roadmap](docs/ROADMAP.md)
- [Contributing](CONTRIBUTING.md)

## Contributing

Issues and pull requests are welcome. Just don’t commit usernames, passwords, cookies, registration numbers, private VTOP HTML, or downloaded course files. Redacted parser fixtures are fine and very useful.

That’s pretty much it. VTOP has the data; Viora just tries to make it nicer to use.

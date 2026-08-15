# Viora

Viora puts the useful parts of VTOP into a cleaner Android app, so you do not have to dig through the portal every time you want to check something.

Sign in with your VTOP account and the app pulls in your classes, attendance, assignments, exams, marks, grades and course materials. It keeps everything synced in the background and saves a local copy, so you can still check most things when VTOP or campus Wi-Fi is having a moment.

## What you can do

- See today's classes, upcoming exams and pending assignments from Home
- Check the full timetable, class timings and rooms
- Track attendance and see how many classes you can skip while staying at 75%
- Get reminders before classes, exams and assignment deadlines
- View marks, grades, GPA and CGPA
- Plan the GPA needed to reach a target CGPA
- Browse courses and download materials to `Downloads/Viora-VIT/<course>`
- Open VTOP's assignment page and upload DAs from the app
- Search through courses and academic info
- Keep things updated with background sync or the manual sync button

## Privacy and security

Viora talks directly to VTOP. There is no Viora server collecting your account or academic data.

Your saved login and VTOP session are encrypted using Android's secure key storage. Timetables, attendance, marks and other synced details stay inside the app on your phone, and Android backups are turned off. Course files you choose to download are normal files in your Downloads folder, so you can open and share them easily.

Logging out clears Viora's saved account and local academic data without logging you out of VTOP in your browser.

## Download the APK

Grab the newest APK from [GitHub Releases](https://github.com/dumbly-smart/viora/releases/latest), open it on your Android phone and tap **Install**.

Android may ask you to allow installs from your browser or file manager since the APK is downloaded directly. You can turn that permission back off after installing. When there is a newer version, install it over the old one to keep your local data.

Only download Viora from this repository's Releases page.

## Build it yourself

Open the project in Android Studio with JDK 17, let Gradle sync and run the `app` configuration on an Android 8.0 or newer phone or emulator.

You can also build it from the terminal:

```bash
gradle testDebugUnitTest assembleDebug lintDebug
```

## Contributing

Found a bug or have a fun idea? Open an issue or send a pull request. Small fixes are welcome too.

Please keep personal VTOP stuff out of commits—no usernames, passwords, cookies, registration numbers, private page dumps or downloaded course files. Redacted test data is totally fine.

More project details are in [`docs`](docs), and the contribution notes are in [CONTRIBUTING.md](CONTRIBUTING.md).

That's Viora: less portal wrestling, more getting things done.

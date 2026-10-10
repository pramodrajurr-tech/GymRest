# Gym Rest: 3-minute rest timer that rings when your phone is locked

Tap **Start 3:00 rest** after each set. A countdown runs in the background and a loud
beep-beep-beep alarm (plus vibration and a lit-up screen) goes off when rest is over,
even with the screen locked or another app open.

## How to use
1. Finish a set, tap **Start 3:00 rest**. The set counter goes up by one.
2. Put the phone away. Lock it, use Instagram or music, anything.
3. When it rings, tap **Stop alarm** (or the "Stop alarm" button in the notification).
4. Change the rest time with the -15 / +15 sec buttons (15 sec to 15 min). It is remembered.

## Get the APK (pick ONE way)

### A) Android Studio (on a PC or laptop)
1. Install Android Studio (free): https://developer.android.com/studio
2. Unzip this folder. In Android Studio: **File > Open** and choose the `GymRest` folder.
3. Wait for "Gradle sync" to finish (first time downloads things, a few minutes).
   If it asks to install Android SDK Platform 34, click Install.
4. Menu: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Click **locate** in the pop-up. The file is `app/build/outputs/apk/debug/app-debug.apk`.

### B) No install: let GitHub build it for free
1. Make a free account at github.com and create a new empty repository.
2. Upload ALL files from this folder (including the hidden `.github` folder) to it.
   Easiest: install "GitHub Desktop", or on a PC use `git` to push the folder.
3. Open the repository's **Actions** tab > **Build APK** > wait ~3 minutes.
4. Open the finished run, download **GymRest-apk** (a zip containing `app-debug.apk`).

## Install on your Vivo phone
1. Send `app-debug.apk` to the phone (USB cable, Google Drive, WhatsApp to yourself...).
2. Open it from the Files app. If Android blocks it, tap **Settings** and allow
   "Install unknown apps" for the app you opened it from.
3. If Vivo's security check or Play Protect warns about an unknown app, choose
   **Install anyway**. The warning appears because the app is not from a store.
4. Open **Gym Rest**. On first launch it shows a checklist screen. Do every item.
   You can reopen it any time with **Phone settings**.

## Vivo-specific settings (important!)
Vivo's battery manager is strict. Without these, the alarm may not ring on a locked phone:
- Settings > Battery > Background power consumption management > Gym Rest >
  **Allow high background power consumption**
- i Manager > App manager > **Autostart** > turn on Gym Rest
  (or Settings > Apps > Autostart)
- Open Recent apps and **lock** Gym Rest (pull its card down / tap the lock icon).
  Never swipe the app away from Recent apps while you are resting. Vivo treats that as
  "force close" and cancels the alarm.
- Raise the **Alarm** volume (Settings > Sounds). The alarm ignores the Media volume.

Use **Test alarm** (10 seconds) to check: tap it, lock the phone, wait.

## Notes
- A small alarm-clock icon appears in the status bar during rest. That is the safety-net
  alarm Android uses to wake the app on time.
- The alarm stops by itself after 2 minutes if you do not touch it.
- No internet access, no ads, no accounts. The app asks for no personal data.
- Built with: Kotlin, no third-party libraries. minSdk 26 (Android 8+), targetSdk 34.

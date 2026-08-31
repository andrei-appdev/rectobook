---
name: run-on-emulator
description: Build, install, launch and screenshot the DjVu Reader app on the Android emulator. Use whenever verifying a change on a device, taking screenshots of a screen, or driving the UI with adb - it carries the JDK, path and shell quirks that this project's toolchain needs.
---

# Running DjVu Reader on the emulator

Everything below is verified on this machine. The two things that waste the
most time if unknown are the JDK (the obvious one fails) and which shell can
capture a screenshot (PowerShell corrupts it).

## Paths

| | |
|---|---|
| JDK | `C:\Users\Andrei\.jdks\jbr-21.0.11` |
| adb | `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` |
| emulator | `%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe` |
| AVD | `djvu_test` (usually already running as `emulator-5554`) |
| APK | `app\build\outputs\apk\debug\app-debug.apk` |
| Activity | `com.cartavu.app/.MainActivity` |

## Build

`JAVA_HOME` is **not** set in the environment. Gradle fails outright without
it:

```
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

Do not reach for Android Studio's bundled JBR
(`C:\Program Files\Android\Android Studio\jbr`) — it is **Java 25**, which AGP
rejects with a message that is only the version number, which reads like a
crash rather than a rejection:

```
* What went wrong:
25.0.2
```

Use the 21 JDK, and give Gradle an absolute project path with `-p`. The
wrapper is not on `PATH`, and a bare `gradlew` fails from any working
directory a previous Bash `cd` may have left behind:

```powershell
$env:JAVA_HOME = 'C:\Users\Andrei\.jdks\jbr-21.0.11'
& 'C:\Projects\djvu_reader\gradlew.bat' -p C:\Projects\djvu_reader :app:assembleDebug --console=plain -q
```

`-q` prints nothing on success. One warning is normal and harmless:

> Warning: SDK processing. This version only understands SDK XML versions up to 3…

The `$env:` assignment does not persist between tool calls — repeat it in
every PowerShell command that runs Gradle, or chain build and install in one
call. Setting `org.gradle.java.home` in `local.properties` would make it
permanent; that file is gitignored and machine-specific, which is the right
home for it.

A clean build compiles DjVuLibre for three ABIs and takes a few minutes;
an incremental Kotlin-only build is seconds.

## Install and launch

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r 'C:\Projects\djvu_reader\app\build\outputs\apk\debug\app-debug.apk'
```

Force-stop before relaunching, or the running process stays up and the new
code never loads:

```bash
./adb.exe shell am force-stop com.cartavu.app
./adb.exe shell am start -n com.cartavu.app/.MainActivity
```

If no device is listed by `adb devices`, start the AVD in the background:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd djvu_test
```

*(Unverified — the emulator has been running in every session so far.)*

## Screenshots

**Must be Bash, not PowerShell.** PowerShell's redirection re-encodes the
binary stream and prepends a UTF-8 BOM, and the PNG will not open:

```bash
cd "/c/Users/Andrei/AppData/Local/Android/Sdk/platform-tools"
./adb.exe exec-out screencap -p > "$SCRATCH/shot.png"
```

Then read the file with the Read tool to see it.

Screenshots come back **1080x2400** and are shown scaled to 900x2000 —
**multiply displayed coordinates by 1.2** to get tap coordinates.

## Driving the UI

```bash
./adb.exe shell input tap <x> <y>
./adb.exe shell input text "netscape"     # no spaces; use %s for a space
./adb.exe shell input keyevent 4          # system back
./adb.exe shell input swipe <x1> <y1> <x2> <y2> <ms>
```

Two limits worth knowing before planning a test:

- `input` is **single-touch**. A pinch cannot be driven, so pinch-zoom
  behaviour has to be exercised through the +/- buttons in the bottom bar,
  which go through the same `zoomAbout` path.
- Prefix any command carrying a **device path** with `MSYS_NO_PATHCONV=1`, or
  Git Bash rewrites `/sdcard/x` to `C:/Program Files/Git/sdcard/x`:

  ```bash
  MSYS_NO_PATHCONV=1 ./adb.exe push file.pdf /sdcard/Download/
  ```

## Logs

The app logs nothing of its own — there is not a single `Log.d` in the
sources — so logcat is only useful for crashes and for what the framework
says:

```bash
./adb.exe logcat -c                                   # clear, then reproduce
./adb.exe logcat -d -s AndroidRuntime:E CRASH:E
./adb.exe logcat -d --pid=$(./adb.exe shell pidof com.cartavu.app)
```

To trace something, add a temporary `android.util.Log` call and filter on its
tag; there is no existing tag to filter on.

## Test documents

The library's recents usually hold `sample.djvu` (57 pp, DjVu with an OCR
layer), `manual.pdf` (4 pp) and a Cyrillic-named copy for font checks. The
bundled sample asset was **removed** in `9ad5bbb` — a fresh install starts
with an empty library, so push a document and open it through the picker:

```bash
MSYS_NO_PATHCONV=1 ./adb.exe push sample.djvu /sdcard/Download/
```

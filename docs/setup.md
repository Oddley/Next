# Development Environment Setup

This guide gets you from a base Windows 11 install to a working Android development environment for this project.

---

## What you already have

| Tool | Status | Notes |
|---|---|---|
| Git | Installed | `git --version` to confirm |
| GitHub CLI (`gh`) | Installed | Already authenticated |
| Android Studio | Installed | At `C:\Program Files\Android\Android Studio` |
| Node.js | Installed | Not needed for this project |

---

## Step 1 — Install JDK 21

Android Studio bundles a JDK internally, but you need one on your `PATH` for command-line Gradle runs (TDD workflow per ADR-003).

1. Go to **https://adoptium.net/temurin/releases/?version=21**
2. Download the **Windows x64 `.msi`** installer (Eclipse Temurin 21 LTS)
3. Run the installer. On the "Custom Setup" screen, ensure these features are enabled:
   - **Add to PATH**
   - **Set JAVA_HOME variable**
4. Complete the install, then open a **new** PowerShell window and verify:
   ```
   java -version
   ```
   Expected output: `openjdk version "21.x.x" ...`

---

## Step 2 — Finish Android Studio first-run setup

Android Studio is installed but the SDK has not been downloaded yet.

1. Launch **Android Studio** from the Start menu
2. The first-run wizard will appear. Choose **Standard** setup
3. Accept all SDK license agreements
4. Let it download the default SDK components (~1–2 GB, takes a few minutes on a decent connection)
5. When the wizard finishes, note the **SDK Location** shown at the bottom of the welcome screen — it will be something like:
   ```
   C:\Users\Branden\AppData\Local\Android\Sdk
   ```

---

## Step 3 — Install required SDK components

In Android Studio, open **SDK Manager** (the toolbar icon, or via Settings → Languages & Frameworks → Android SDK).

**SDK Platforms tab** — ensure these are installed:
- Android 16.0 (API 36) — latest stable, default selection
- Android 15.0 (API 35) — the project's declared `targetSdk`

**SDK Tools tab** — ensure these are installed:
- Android SDK Build-Tools 37.x (latest, default selection)
- Android Emulator
- Android SDK Platform-Tools

**Hypervisor note (AMD CPUs):** Do **not** install "Android Emulator Hypervisor Driver for AMD Processors" from the SDK Tools tab — it's broken and will error on install. AMD emulator acceleration on Windows uses the built-in **Windows Hypervisor Platform** instead; see Step 3a below.

Click **Apply** and let everything download.

---

## Step 3a — Enable Windows Hypervisor Platform (AMD CPUs only)

*Skip this step if you have an Intel CPU — HAXM (installed via SDK Manager) handles acceleration for Intel.*

The Android Emulator on AMD processors uses **WHPX** (Windows Hypervisor Platform), a built-in Windows feature.

1. Open **Start → "Turn Windows features on or off"**
2. Scroll down and check **Windows Hypervisor Platform**
3. Click OK and **reboot**

After rebooting, the emulator will use WHPX acceleration automatically.

---

## Step 4 — Set environment variables

1. Open **Start → "Edit the system environment variables"** → click **Environment Variables**
2. Under **System variables**, click **New** and add:
   - Variable name: `ANDROID_HOME`
   - Variable value: `C:\Users\Branden\AppData\Local\Android\Sdk`
     *(adjust if the SDK manager showed a different path in Step 2)*
3. Find the **Path** variable under System variables, click **Edit**, then add these two entries:
   - `%ANDROID_HOME%\platform-tools`
   - `%ANDROID_HOME%\emulator`
4. Click OK everywhere to save, then open a **new** PowerShell window

---

## Step 5 — Create an Android Virtual Device (AVD)

1. In Android Studio, open **Device Manager** (right sidebar or View → Tool Windows → Device Manager)
2. Click **Create Virtual Device**
3. Choose a phone profile — **Pixel 8** or **Pixel 9** is recommended
4. Select a system image: **API 36 (Android 16)**, x86_64 — download it if not already present (API 35 also works)
5. Finish the wizard; the AVD will appear in Device Manager

---

## Step 6 — Verify everything works

Open a fresh PowerShell window and run:

```powershell
java -version          # should show openjdk 21
adb --version          # should show Android Debug Bridge version 1.x.x
```

Then in Android Studio:
- Open this project folder (`C:\Users\Branden\ClaudeNext`)
- Android Studio will detect it as an Android project once `build.gradle.kts` is added in Phase 1
- After Phase 1 scaffolding, hit **Sync Project with Gradle Files** and confirm it completes without errors

---

## Notes

- **Gradle wrapper** (`gradlew.bat`) will be generated when the project is scaffolded in Phase 1 of the implementation plan. You do not need to install Gradle globally.
- **Running domain tests from the command line:** `.\gradlew test` — this is the primary TDD loop (ADR-003). It uses the JDK you installed above, not Android Studio's bundled one.
- **Kotlin** is included as a Gradle plugin dependency; no separate installation needed.
- **Drive sync** (Phase 3) will require enabling the Google Drive API in a Google Cloud project and adding an OAuth client ID. Instructions will be added when Phase 3 begins.

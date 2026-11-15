# JARVIS – setup guide

## 1. Get the API keys
**Picovoice (wake word "Jarvis")**
1. Sign up free at https://console.picovoice.ai
2. Copy your **AccessKey** from the dashboard. ("Jarvis" is a built-in keyword, no model file needed.)
3. The key is checked online at start-up, so keep internet on the first time.

**Gemini**
1. Open https://aistudio.google.com/apikey and sign in.
2. Click **Create API key**, copy it.
3. Default model is `gemini-flash-latest` (always the newest Flash). You can type another model name in Settings, e.g. `gemini-3.8-flash`.

Paste both keys in the app: **Settings → Gemini API key / Porcupine AccessKey → SAVE**.

## 2. Build and install the APK
1. Install **Android Studio** (Ladybug or newer, JDK 17 bundled).
2. **File → Open** the `JARVIS` folder, wait for Gradle sync (needs internet once).
3. Phone: Settings → About → tap *Build number* 7 times → Developer options → enable **USB debugging**.
4. Plug in the phone, press **Run ▶** to install directly.
   Or build an APK: **Build → Build Bundle(s)/APK(s) → Build APK(s)**, then copy
   `app/build/outputs/apk/debug/app-debug.apk` to the phone and tap it (allow "install unknown apps").
5. Command line alternative (needs Gradle installed once): `gradle wrapper` then `./gradlew assembleDebug`.

## 3. First-run permissions (Settings screen has a button for each)
1. **Grant** – microphone, notifications, calls, SMS, contacts.
2. **Accessibility** – Settings → Accessibility → Installed apps → JARVIS → On.
   If the switch is greyed out (sideloaded app, Android 13+): phone Settings → Apps → JARVIS → ⋮ (top right) → **Allow restricted settings**, then try again.
3. **Notification access** – enable JARVIS (lets it read notifications aloud).
4. **Display over other apps** – needed so Android lets JARVIS open apps/alarms while in the background.
5. **Modify system settings** – for brightness.
6. **Battery → Unrestricted** (Settings → Apps → JARVIS → Battery → Unrestricted, or the button in the app). On Xiaomi/Oppo/Vivo/Samsung also enable *Autostart* / "Never sleeping apps" and lock JARVIS in the recents screen, otherwise the OS kills the listener.
7. **Exact alarms** – for reminders.

Then press **ACTIVATE**. Say "Jarvis" → beep → "Yes, sir?" → speak your command.
Say "Jarvis band ho jao", "stop" or "go to sleep" → "Going offline, sir" and it returns to wake-word-only mode.

## Notes / limits
- Always-on mic service must be started while the app is on screen (Android 14+ rule). After a reboot, open the app once and press ACTIVATE.
- Wi-Fi/Bluetooth toggling on Android 10+ is done through Accessibility (opens the settings page, flips the switch, goes back).
- Reminders are lost if the phone reboots.
- Speech recognition uses Google's recognizer; for Hinglish keep language on **Hindi / Hinglish**.

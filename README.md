# Guardian - Open Source Accountability App

Guardian is a powerful, privacy-first, on-device accountability application designed to help individuals break free from adult content addiction. Unlike traditional parental control apps that rely on expensive cloud subscriptions or can be easily bypassed, Guardian is built to be un-bypassable and entirely free.

## Mission
To provide a premium-grade, un-bypassable accountability tool completely free of charge for those who are struggling with addiction and cannot afford the $10-$15 monthly fees charged by other apps.

## Key Features

🛡️ **4-Wall Protection Architecture**
1. **Accessibility Sentry:** A deep-system overlay that monitors the screen for explicit content and instantly blocks it locally, preventing any bypass via incognito modes or alternative browsers.
2. **Local DNS VPN:** A lightweight, on-device VPN that intercepts and null-routes domains known to host explicit content, without ever sending your browsing history to a remote server.
3. **App Blocking & Uninstall Protection:** Prevents the user from uninstalling the app or killing the background services without authorization.
4. **Device Administrator:** Hooks into Android's core to lock down the device from unauthorized tampering.

🔒 **Trusted Person Accountability**
You cannot disable Guardian on your own. If you attempt to uninstall the app or turn off the protection walls, a secure, cryptographic 24-hour cooloff period begins. You must enter an emergency unlock code that is ONLY given to your Trusted Person via SMS.

⏱️ **Emergency Sanctuary Mode**
A built-in grounding tool designed to help users navigate intense urges. It features a calming, immersive breathing exercise (Inhale 4s, Hold 7s, Exhale 8s) and a one-tap emergency call button to your Trusted Person.

📈 **Resilient Streak Tracking**
A completely offline, tamper-proof day streak counter that tracks your clean days accurately using system-time independent metrics.

🔋 **Auto-Resurrection Engine**
If Android tries to kill the Guardian background service to save battery, our custom `WatchdogReceiver` and `GuardianJobService` instantly resurrects the protection layer, ensuring there are zero drops in coverage.

## Privacy First
**Zero Cloud Infrastructure.** All text analysis, domain blocking, and streak tracking occur entirely locally on your device. We do not track your browsing history.

## Setup Instructions

### Prerequisites
- Android Studio Ladybug or newer
- Minimum SDK: 24 (Android 7.0)

### Building the App
1. Clone this repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/Guardian.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle and click `Run` (or run `.\gradlew assembleDebug` from the command line).

### Contributing
This project is fully open-source. Pull requests for new features, bug fixes, and expanded blocklists are heavily encouraged!

## License
MIT License. Free forever.

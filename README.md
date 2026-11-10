# Synkromesh

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android 15 Ready](https://img.shields.io/badge/Android%2015-16%20KB%20Page%20Aligned-success.svg)](https://developer.android.com/guide/practices/page-sizes)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-brightgreen.svg)](https://developer.android.com/jetpack/compose)

Synkromesh is an offline peer-to-peer audio intercom application for Android. It routes audio between devices over local Wi-Fi without cellular data or internet connectivity, supporting Bluetooth SCO headsets and helmet communicators.

---

## Architecture Overview

```
                      ┌───────────────────────────────────────────────┐
                      │              Synkromesh App                   │
                      │        (Android 8.0+ / Target SDK 35)         │
                      └───────┬───────────────────────────────┬───────┘
                              │                               │
             Bluetooth SCO / HFP Profile            Local Offline Network
                              │                               │
            ┌─────────────────┴───────────────┐  ┌────────────┴─────────────────┐
            │ Helmet Headset / Audio Device   │  │  Google Nearby Connections   │
            │   • Bluetooth SCO Audio Routing │  │        (P2P_CLUSTER)         │
            │   • Media Button Handling       │  │              +               │
            │   • Built-in Mic / Speaker      │  │ UDP Broadcast (Port 50005)   │
            └─────────────────────────────────┘  └──────────────────────────────┘
```

---

## Technical Specifications

- **Audio Pipeline:**
  - Opus codec (RFC 6716) at 16 kHz / 24 kHz / 48 kHz.
  - 20ms frame sizes with native Packet Loss Concealment (PLC).
  - Isolated transmission (Tx) and reception (Rx) threads for full-duplex operation.
  - Hardware echo cancellation (AEC) and noise suppression (NS) via Android audio effects where available.
- **Networking:**
  - P2P discovery and clustering via Google Nearby Connections (`P2P_CLUSTER`).
  - Local-only Hotspot / Wi-Fi UDP datagram broadcast on port `50005`.
  - Operates entirely offline without internet, cell towers, or external servers.
- **Transmission Controls:**
  - Push-to-Talk (PTT): Hold to transmit audio.
  - Toggle: Tap once to open audio uplink, tap again to mute.
  - Continuous (Always-On): Continuous transmission with voice activity detection (VAD).
- **Bluetooth & Background Service:**
  - Bluetooth SCO (Hands-Free Profile) mic and speaker routing.
  - Android Foreground Service with `PARTIAL_WAKE_LOCK` and Wi-Fi low-latency lock.
  - Media button handling via `MediaSessionCompat` to toggle transmission from connected headsets.
  - Audio focus management with ducking for navigation prompts.
- **Compatibility & Standards:**
  - Minimum SDK: Android 8.0 (API 26); Target SDK: Android 15 (API 35).
  - 16 KB memory page size alignment (`-Wl,-z,max-page-size=16384`).
  - Native library (`libsynkromesh_audio.so`) built with CMake and Android NDK 27.

---

## Device Support

Any audio input/output device supported by Android Bluetooth SCO or standard audio routing:
- Motorcycle helmet communicators (EJEAS, FreedConn, BT-S2, Cardo, Sena, etc.)
- Standard Bluetooth headsets and earbuds
- Built-in device microphone and speaker

---

## Building from Source

### Prerequisites
- Android Studio 2024.2+ or Android SDK command-line tools.
- Android NDK: 27.0.12077973 (or CMake 3.22.1+).
- JDK: OpenJDK 21.

### 1. Clone Repository
```bash
git clone https://github.com/landxcape/Synkromesh.git
cd Synkromesh
```

### 2. Configure Keystore (Optional for Release)
Copy the example properties file and provide your signing credentials:
```bash
cp keystore.properties.example keystore.properties
```

### 3. Build APKs
```bash
# Debug build:
./gradlew assembleDebug

# Release build (generates ABI-split APKs in app/build/outputs/apk/release/):
./gradlew assembleRelease
```

---

## License

Synkromesh is free software licensed under the **GNU General Public License v3.0 (GPLv3)**. See [LICENSE](LICENSE) for details.

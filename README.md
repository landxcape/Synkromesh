# Synkromesh

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android 15 Ready](https://img.shields.io/badge/Android%2015-16%20KB%20Page%20Aligned-success.svg)](https://developer.android.com/guide/practices/page-sizes)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-brightgreen.svg)](https://developer.android.com/jetpack/compose)

**Synkromesh** converts budget motorcycle Bluetooth helmet headsets (EJEAS, FreedConn, BT-S2, standard Bluetooth add-on speakers/mics) into a low-latency, full-duplex intercom mesh without requiring expensive (\$300+) proprietary systems (Cardo/Sena) or cellular data.

---

## Architecture Overview

```
                      ┌───────────────────────────────────────────────┐
                      │              Synkromesh App                   │
                      │        (Android 8.0+ / Target SDK 35)         │
                      └───────┬───────────────────────────────┬───────┘
                              │                               │
             Bluetooth SCO / HFP Profile            Offline P2P Mesh Network
                              │                               │
            ┌─────────────────┴───────────────┐  ┌────────────┴─────────────────┐
            │ Helmet Headset (Mic & Speakers) │  │  Google Nearby Connections   │
            │   • EJEAS V6 / FreedConn T-COM  │  │        (P2P_CLUSTER)         │
            │   • BT-S2 / Cardo / Sena        │  │              +               │
            │   • Headset Button Interception │  │ UDP Broadcast (Port 50005)   │
            └─────────────────────────────────┘  └────────────┬─────────────────┘
                                                              │
                                                     Bike-to-Bike Range
                                                  (Up to 100+ meters outdoor)
```

---

## Key Features

- **100% Full-Duplex Audio Engine:**
  - Native **Opus Codec** (RFC 6716) wideband voice at 16 kHz / 24 kHz.
  - Ultra-low latency: 20ms audio frame sizes with native Packet Loss Concealment (PLC).
  - Independent transmission and reception pipelines; incoming audio is **never** blocked or muted while speaking.
- **Zero-Config Offline Mesh:**
  - Automated bike discovery and clustering over BLE + Wi-Fi Direct using Google Nearby Connections (`P2P_CLUSTER`).
  - Fallback local UDP multicast/broadcast socket on port `50005`.
  - Works completely offline in mountain passes, canyons, and rural roads with **zero cell service or mobile data**.
- **Real-Time Rider Radar & Distance Estimation:**
  - Hybrid distance calculation: High-precision GPS mesh telemetry via Haversine distance formula + relative compass bearing (`Ahead`, `Behind`, `Left`, `Right`).
  - Automatic RF path-loss fallback (BLE/Wi-Fi RSSI in dBm) when in tunnels or before satellite acquisition.
- **3 Glove-Friendly Transmission Modes:**
  - **Push-To-Talk (PTT):** Touch-and-hold the oversized center button or hold the helmet button.
  - **Toggle (Latch):** Tap once to open microphone uplink, tap again to mute.
  - **Always-On (Open Intercom):** Hands-free continuous transmission filtered by Voice Activity Detection (VAD) and noise gating to suppress exhaust/wind rumble.
- **Pocket Resilience & Battery Endurance:**
  - Runs inside an Android Foreground Service with dual locks (`PARTIAL_WAKE_LOCK` + `WIFI_MODE_FULL_LOW_LATENCY`).
  - Audio focus ducking for navigation prompts (Google Maps, Waze).
  - Headset button interception (`MediaSessionCompat`) to toggle mic mute while phone is safely zipped in a riding jacket.
- **Android 15 & 16 KB Page-Size Ready:**
  - Fully compliant with Android 15 16 KB memory page alignment (`-Wl,-z,max-page-size=16384`).
  - Native library (`libsynkromesh_audio.so`) loaded directly uncompressed from APK for maximum memory efficiency.

---

## Hardware Compatibility Matrix

| Category | Tested Models | Connectivity |
| :--- | :--- | :--- |
| **Budget Helmet Headsets** | EJEAS V6 Pro, E6, V4 | Bluetooth SCO / Handsfree Profile (HFP) |
| **Mid-Tier Helmet Headsets** | FreedConn T-COM SC, T-MAX, FX | Bluetooth SCO / Handsfree Profile (HFP) |
| **Classic Motorcycle Units** | BT-S2, BT-S3 | Bluetooth SCO / Handsfree Profile (HFP) |
| **Premium Systems** | Cardo Packtalk, Freecom / Sena 50S, 30K | Paired as standard Bluetooth Phone Headset |
| **Standard Audio Devices** | AirPods, Galaxy Buds, wired 3.5mm / USB-C | Handled automatically via Android Audio Router |

---

## Building from Source

### Prerequisites
- **Android Studio Ladybug (2024.2+)** or command-line SDK tools.
- **Android NDK:** Version 27+ (or CMake 3.22.1+).
- **JDK:** OpenJDK 21.

### 1. Clone Repository
```bash
git clone https://github.com/landxcape/Synkromesh.git
cd Synkromesh
```

### 2. Configure Keystore (Optional for Release)
Copy the example properties file and fill in your signing credentials:
```bash
cp keystore.properties.example keystore.properties
```

### 3. Build Release APK
```bash
./gradlew assembleRelease
```
The output APK is located at:
```text
app/build/outputs/apk/release/app-release.apk
```

### 4. Install & Launch on Device
```bash
./gradlew installRelease && adb shell am start -n com.landxcape.synkromesh/.presentation.MainActivity
```

---

## License

Synkromesh is free and open-source software licensed under the **GNU General Public License v3.0 (GPLv3)**. See [LICENSE](LICENSE) for details.

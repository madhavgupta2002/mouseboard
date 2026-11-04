# AirMouse

A working prototype that turns a Samsung Galaxy into a motion mouse for a Mac.
Tilt the phone up/down and side to side, and tap the left/right pads to click.
The apps communicate directly over your local network. There is no account,
cloud service, or subscription.

## Start here

The built Mac app is **`dist/AirMouse.app`**. The phone installer is
**`dist/AirMouse-Android.apk`**. Double-click **`Open AirMouse.command`** to open
the Mac app, or open the app directly in Finder.

1. **Use the same Wi-Fi.** Connect the Mac and Galaxy S24 Ultra to the same
   trusted private network. Keep AirMouse open on the Mac.
2. **Allow the Mac to move the pointer.** Click **Open Settings** in AirMouse.
   In **Privacy & Security → Accessibility**, enable **AirMouse**. If it is not
   listed, use **+** to select the built app. The status in AirMouse changes
   automatically. If macOS asks for **Local Network** access, allow it too.
3. **Install the Android app.** Scan the QR code shown on the Mac using the
   Samsung camera, or type the full displayed installation link in your phone's
   browser. Tap **Download Android app**, open the APK, and approve installation
   from your browser. This is a locally built development APK, not a Play Store
   release. Only install it if you trust this project. You can revoke your
   browser's “Install unknown apps” permission afterward.
4. **Pair.** Return to the installation page in the phone browser. Tap
   **Open AirMouse & fill pairing details**, then **Connect to Mac** in the app.
   Alternatively, enter the Mac address and 12-character code manually.
5. **Calibrate.** Hold the phone in portrait orientation, screen facing up near
   horizontal, with its top pointing away from you. Tap **Recenter**.
6. **Move and click.** Raise/lower the phone's top edge for vertical movement.
   Tilt the right edge down to move right, and the left edge down to move left.
   Tap **LEFT** or **RIGHT** to click. Two quick left taps send a double-click.

Start with small movements. Change **Sensitivity** on the phone to taste.
For remote-control-style pointing, turn on **Turn left/right instead of tilting**;
horizontal movement then follows turning the phone left/right.

The phone app must stay in the foreground. Switching apps, locking the phone,
or a lost connection disconnects it. Tap **Connect to Mac** to reconnect.
**Pause** works on either device. **⌘⇧P** pauses/resumes while the Mac app is
focused. The Mac also has an AirMouse menu bar control.

## If something does not connect

- **Samsung blocks the APK:** check **Settings → Security and privacy →
  Auto Blocker**. If you choose to allow this local APK, temporarily turn off
  Auto Blocker, install, then restore it. Managed devices may prohibit
  sideloading; do not bypass organization policies.
- **QR page will not load:** verify the same network, choose the correct address
  in the Mac app if it lists several, and allow AirMouse through the macOS
  firewall and Local Network settings. The whole link, including its random
  path, is required.
- **Guest/office Wi-Fi:** it may prevent devices talking to each other. Try a
  private home network or a hotspot with both devices on the same network.
- **Connected but no pointer movement:** enable AirMouse under Accessibility
  and resume control on both devices. If you moved or rebuilt the Mac app,
  remove the old Accessibility entry and add the current app again.
- **Pairing fails:** enter the latest code shown on the Mac, or reopen the
  latest QR page. A new code invalidates old installation links. After ten
  unsuccessful handshakes, wait one minute.
- **Movement is awkward:** return near horizontal and tap Recenter. Avoid
  rotating past vertical in tilt mode. Try the alternate horizontal mode.
- **Receiver cannot start:** another app or copy of AirMouse may be using TCP
  ports **47821** or **47822**. Quit the extra copy.

## Why motion sensors instead of just the compass?

A compass alone measures heading; it does not provide smooth, full tilt
tracking. AirMouse uses Android's **game rotation vector**, a fused relative
orientation signal that avoids relying on the magnetic compass. It falls back
to the standard rotation vector on phones without that sensor.

Recenter sets a neutral orientation. The app measures changes relative to that
orientation, applies a time-based smoothing filter, and sends pointer deltas at
up to about 60 packets per second. A stationary phone stops the pointer.
Click touches briefly suppress motion to reduce accidental jitter.

Reference: [Android position sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position).

## Build from source

Requires macOS 14 or later, Apple's Command Line Tools with Swift 5.9 or later,
Java 17 or later, and Python 3.9 or later. Android Studio, Gradle, and paid Apple
developer membership are not needed for this local build.

```sh
bash scripts/build-all.sh
open dist/AirMouse.app
```

The build downloads two pinned official Google Android SDK archives into
`.tools/android`, checks them against Google's published checksums, compiles
the Java app, creates a local development signing key, and signs the APK.
On an Apple Silicon Mac, Google's x86 SDK executables may require Rosetta.
The Mac app is locally ad-hoc signed.

Keep `.tools/airmouse-debug.keystore` if you want future APKs to update an
existing installation without uninstalling. This is a development key, not a
release signing arrangement. The key and SDK are ignored by Git.

The Mac build embeds the APK so the QR installation page works without running
a separate server or installing any software on the Mac beyond AirMouse.

```text
mac/Sources/AirMouse/           SwiftUI app, TCP receiver, mouse events, install server
mac/Sources/AirMouseCore/       Framing, validation, multi-display pointer bounds
mac/Tests/AirMouseCoreChecks/   Standalone checks; full Xcode/XCTest not needed
android/app/src/main/java/     Android UI, sensor math, TCP client
scripts/                      Build and test commands
dist/                         Ready-to-open app and ready-to-install APK
```

## Verify

Quit the Mac app before running integration tests:

```sh
bash scripts/test.sh
```

Tests cover protocol framing and bounds, calibrated motion directions,
stationary behavior, pairing rejection, the single-phone limit, both clicks,
heartbeat/disconnect handling, installation downloads, and protected links.
Integration tests run the actual Mac receiver in diagnostic mode; they log
events instead of moving your real pointer.

Testing on a physical S24 Ultra and approving macOS Accessibility remain
manual steps. The build environment cannot provide the phone's sensor readings
or grant these permissions for you.

## MVP scope

- Mac receiver and Android controller; no Windows/Linux receiver yet.
- Left/right click and double-click; scrolling and dragging are not implemented.
- Local IPv4 networking; no Bluetooth, USB, Internet relay, or automatic discovery.
- One connected phone. A random pairing code authorizes input.
- The installer is protected by a separate random link.
- Traffic is **not encrypted**. Use a trusted private network; this prototype
  does not protect against someone capturing traffic on that network.
- Built APK targets Android API 35 and supports Android 8 or later.
- The Mac build targets the architecture of the build machine.
- Neither binary is a notarized/store-distributed release.

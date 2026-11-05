# AirMouse — Bluetooth edition

Your Galaxy S24 Ultra acts as a standard Bluetooth mouse. Tilt the phone to move
the Mac pointer, and tap the left/right pads to click. All control traffic goes
directly over Bluetooth; the Android app has no Internet permission.

## Install and connect

1. Keep Bluetooth on. Your phone and Mac should already be paired in their system
   Bluetooth settings.
2. Open `dist/AirMouse.app` (or double-click `Open AirMouse.command`). The Mac app
   is now a setup helper; it does not receive or inject mouse events.
3. Select your phone and click **Send APK over Bluetooth**. Accept the incoming
   file on the phone. If macOS asks whether AirMouse may control Bluetooth File
   Exchange, allow it to start this transfer. Alternatively, use **Show APK in
   Finder**, then send the file with macOS Bluetooth File Exchange yourself.
4. On the phone, open the received `AirMouse-Android.apk` and install the update.
   Allow installation from the source Android identifies when it asks. If Samsung
   Auto Blocker prevents sideloading, use the phone's security settings to decide
   whether to allow this locally built app; managed-device policy may prevent it.
5. Open **AirMouse** on the phone, tap **Connect over Bluetooth**, and allow
   **Nearby devices**. Choose your already-paired Mac from the list.
6. Keep the phone app visible. Hold it screen-up near horizontal and tap
   **Recenter**. Tilt its top edge up/down for vertical movement and tilt its
   sides for horizontal movement. Tap **LEFT** or **RIGHT** to click.

You can quit the Mac setup helper after installation. macOS receives the phone
as a real Bluetooth mouse, so this edition needs no Mac Accessibility permission,
IP address, QR code, Wi-Fi, server, or company-network access. The phone's Pause
button releases held mouse buttons and stops movement.

## If the Mac does not accept the mouse connection

Ordinary phone pairing and the Bluetooth mouse profile are different things.
If the phone app times out while connecting:

1. Keep AirMouse open on the phone so its mouse profile stays registered.
2. Remove/forget the old phone pairing in Mac Bluetooth settings, then pair it
   again while AirMouse is open. Return to the phone app and choose the Mac.
3. Close other phone apps that act as Bluetooth keyboards/mice; Android allows
   only one app to register the HID device profile at a time.
4. Check AirMouse's **Nearby devices** permission and that Bluetooth is on.
5. If the app reports that the HID device profile is unavailable, that phone's
   firmware does not expose the required Android profile. Pairing alone cannot
   add the profile.

The controller deliberately disconnects when the phone app goes into the
background or the phone locks. Reopen it and tap **Connect over Bluetooth**.

## Implementation

- Android `BluetoothHidDevice` registers a mouse-only descriptor. It sends
  three-byte reports: mouse buttons, relative X, and relative Y.
- The descriptor uses no report ID and matches the three-byte boot mouse format.
  It contains no keyboard usages. Both report and boot protocol modes use the
  same bytes.
- Left/right clicks have separate press and release reports. Large movements are
  split into signed-byte deltas, fractional motion is retained, and pause/stop
  releases buttons before disconnecting.
- Android's game rotation vector supplies calibrated orientation. No magnetic
  compass or camera is required. A standard rotation vector is the fallback.
- The Mac helper uses IOBluetooth to display paired devices and Apple's Bluetooth
  File Exchange to transfer the installer. It opens no network listener.

## Build

Requires macOS 14+, Apple's Command Line Tools, Java 17+, and Python 3.9+.
Android Studio, Gradle, an Apple developer account, and a Play Store account are
not needed for this local prototype.

```sh
bash scripts/build-all.sh
open dist/AirMouse.app
```

The Android build uses pinned official Google SDK components under `.tools` and
signs with `.tools/airmouse-debug.keystore`. Keep that key to install future
updates over the current app. The current APK is version **0.2.0**, version code
**2**, and supports Android 9+ devices whose firmware enables HID device mode.
The Mac app is locally ad-hoc signed.

## Verify

```sh
bash scripts/test.sh
```

The checks cover calibrated sensor motion, the real HID report descriptor,
fractional/clamped movement, left/right and double-click ordering, queue limits,
button release, APK permissions/version, and signatures. The APK must contain
Bluetooth permissions only and no Internet permission.

The Mac companion can also print its view of the system pairing:

```sh
dist/AirMouse.app/Contents/MacOS/AirMouse --diagnostics
```

A physical phone is still needed to verify registration, the Bluetooth HID link,
and actual cursor movement. Automated packet checks do not prove that the phone
has accepted or sent those packets over its Bluetooth radio.

## Files

- `android/app/src/main/java/dev/airmouse/` — phone UI, motion math, HID connection,
  and mouse-report queue.
- `mac/Sources/AirMouse/` — Bluetooth setup and installer transfer helper.
- `scripts/` — local build and verification.
- `dist/AirMouse-Android.apk` — install this on the phone.
- `dist/AirMouse.app` — optional Mac setup helper.
- `dist/AirMouse-WiFi-source-backup.tar.gz` — source snapshot of the previous
  Wi-Fi edition, preserved before the Bluetooth conversion.

Scrolling, dragging, background phone operation, and a store release are outside
this MVP. The existing left/right pads provide clicks and double-clicks.

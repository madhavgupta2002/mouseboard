# MouseBoard

Turn an Android phone into a Bluetooth mouse and keyboard for a Mac or Windows
PC. Tilt to move the pointer or switch to a touchpad, hold to drag, swipe to
scroll, and type with the phone's normal keyboard.

Touchpad mode includes tap/double-tap, tap-then-drag, two-finger scrolling with
momentum, two-finger right-click, pinch zoom and three-finger desktop/window
shortcuts. Pointer speed and scroll direction can be changed in Settings.

MouseBoard uses standard Bluetooth HID. The desktop apps help with pairing and
installing the Android controller; input goes directly to the operating system.
No Wi-Fi connection, account or desktop input-injection service is required.

## Get started

1. Pair the Android phone with the computer.
2. Use the macOS or Windows companion to locate and transfer
   `MouseBoard-Android.apk`.
3. Install the APK, open **MouseBoard**, allow **Nearby devices**, and tap
   **Connect**.
4. Select the computer, hold the phone screen-up, and tap **Recenter**.

See [installation and controls](docs/setup.md) for detailed instructions and
current limits. The Android controller supports Android 9+ devices whose firmware
exposes Bluetooth HID device mode. The macOS companion requires macOS 14+.
See the [Windows companion](apps/windows/README.md) for its requirements and build.

## Build and test

On a Mac with Apple's Command Line Tools, Java 17+ and Python 3.9+:

```sh
bash scripts/build-all.sh
bash scripts/test.sh
open dist/MouseBoard.app
```

The build downloads pinned Android SDK components into `.tools/`. Build outputs,
SDKs and local signing keys stay outside version control.

Run the portable Java logic checks without building desktop artifacts:

```sh
bash scripts/test.sh --unit-only
```

Build the Windows companion on Windows using `scripts/build-windows.ps1`.
See [development](docs/development.md) for the build layout, protocol and
verification details.

## Repository layout

```text
apps/
  android/        Phone controller and native keyboard bridge
  macos/          SwiftUI installer and pairing companion
  windows/        Windows installer and pairing companion
docs/             Setup, development and project history
scripts/          Builds, SDK bootstrap and verification
tests/
  android/        Motion, mouse-report and keyboard-report tests
```

## Current limits

- Typing supports English text and US punctuation. Use **ABC / US** as the
  computer's input source. Emoji and arbitrary Unicode are not implemented.
- The connection runs in a foreground service. Tilt mode can keep moving the
  pointer with the display off; screen taps need the display on. **Dim** provides
  a black, barely lit touch surface.
- Sideways swipes use Shift + wheel; horizontal scrolling depends on the
  desktop application's support for that shortcut.
- Pinch and three-finger gestures send keyboard shortcuts. They need compatible
  app/OS shortcuts and do not turn the phone into a native Apple trackpad.
  Force Click is not implemented.
- Recenter sends an absolute pointer midpoint. Display mapping is controlled by
  the host OS and needs physical-device verification, especially with multiple
  monitors.
- Firmware may omit HID device mode. Pairing alone cannot add that capability.

MouseBoard was originally developed as AirMouse. Android's `dev.airmouse`
application ID and the macOS `dev.airmouse.mac` bundle identifier remain stable
so existing installations can be updated without losing their identity.

See [development history](docs/development-history.md) for the recorded
milestones and provenance of the reconstructed early commits.

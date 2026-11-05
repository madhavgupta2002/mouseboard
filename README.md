# AirMouse — Bluetooth edition

A minimal black controller for Galaxy phones. Tilt to move the Mac pointer,
hold a click pad to drag, swipe the middle strip to scroll, and use the phone's
normal keyboard to type on the Mac. All input travels over Bluetooth HID.
The Android app has no Internet permission.

## Install or update

1. Open `dist/AirMouse.app` or `Open AirMouse.command` on the Mac.
2. Select your paired phone and choose **Send APK over Bluetooth**.
   Accept the file on the phone, open it, and install the update. The same APK
   is also copied to `~/Documents/AirMouse-Android.apk` when prepared for use.
3. Open **AirMouse** on the phone, allow **Nearby devices**, and tap **Connect**.
   Select your Mac.
4. For this 0.3.0 upgrade, macOS may have cached the old mouse-only descriptor.
   If new controls fail, forget the phone in Mac Bluetooth settings, remove the
   old Mac pairing on the phone, pair again, and reconnect in AirMouse.
   Keep AirMouse open while reconnecting.

The Mac app is an optional installer helper. Once installed, input goes directly
to macOS; the helper can be closed. No Accessibility permission or Wi-Fi is used.
If Android asks, allow installation from the source it identifies. Managed phone
policy may prevent installation of locally built APKs.

## Controls

- **Recenter:** resets the current phone grip as neutral and sends a separate
  absolute pointer report at the midpoint of the host's mapped display area.
  On a single display this is the centre of the screen; multi-display mapping is
  controlled by macOS. It releases any held mouse buttons first.
- **LEFT / RIGHT:** tap to click, or keep a finger pressed and tilt to drag.
  Button state persists across motion and wheel reports. Lifting your finger,
  cancelling the touch, pausing or disconnecting releases it.
- **Middle strip:** slide vertically to send wheel events. The pointer stays
  still while you use the scroll strip.
- **Keyboard:** opens your phone's default keyboard in the lower screen area.
  Click a text field on the Mac first. The phone field displays recently typed
  text; it does not read or mirror the Mac's document. English composition is
  sent as you type; autocorrection replaces the changed suffix.
- **Pause:** stops input and releases mouse buttons and keyboard keys.
- **⋯:** sensitivity, tilt/turn mode, and Bluetooth settings.

Keep the phone app in the foreground. It intentionally disconnects if you
switch apps or lock the phone.

### Keyboard scope

Use **ABC / US** as the Mac input source. This version maps English letters,
numbers, US punctuation, Tab, Enter and Backspace into standard keyboard reports.
It compensates for the Mac's Caps Lock state when the host supplies LED updates.
The phone can use Samsung Keyboard, Gboard, or another normal IME.

Emoji, other scripts and arbitrary Unicode are not implemented. Unsupported
edits are rejected with a message, without partially deleting the Mac text.
Large pastes are bounded; if the typing queue fills, wait for it to drain and retry.

## Motion filtering

The game rotation vector supplies calibrated orientation (standard rotation
vector is the fallback). Each axis uses adaptive smoothing with stronger
filtering near rest and faster response during deliberate movement, plus a
small angular hysteresis. Slow motion accumulates across sensor samples.
Sensor gaps and discontinuities reset the filter to prevent delayed jumps.
This follows the speed-adaptive approach of Casiez, Roussel and Vogel's
*1 € Filter* with angular wrapping and position hysteresis.

## Bluetooth reports

Version 0.3.0 registers a composite mouse/keyboard descriptor in report mode:

| Report ID | Input bytes | Purpose |
| --- | --- | --- |
| 1 | Buttons, signed X, signed Y, signed wheel | Relative motion, click/drag and scrolling |
| 2 | Modifier, reserved, six key usages | Standard keyboard |
| 3 | Unsigned 16-bit X and Y, range 0–32767 | Explicit absolute centering |

The host can send a one-byte keyboard LED output report on ID 2.
Mouse transitions and key-down/key-up pairs are preserved in bounded queues.
Pause/disconnect sends neutral mouse and keyboard reports. Modern macOS report
mode is required; pre-boot keyboard operation is outside this MVP.

## Build and verify

Requires macOS 14+, Apple's Command Line Tools, Java 17+ and Python 3.9+.
Android SDK components are pinned under `.tools`; Android Studio and Gradle
are not needed.

```sh
bash scripts/build-all.sh
bash scripts/test.sh
open dist/AirMouse.app
```

The APK is **0.3.0**, version code **4**, with Android 9 as its minimum.
Keep `.tools/airmouse-debug.keystore` to install future updates over the current
app. The Mac app is ad-hoc signed.

Tests cover noisy and deliberate motion, recentering, sensor discontinuities,
HID descriptor lengths and report IDs, drag persistence, release/cancellation,
wheel accumulation, keyboard mapping and queue limits, IME suffix edits,
Bluetooth-only permissions, versions and signatures.

Host report encoding tests do not replace a physical-phone test. The actual
phone keyboard layout, HID reconnection, display mapping and cursor centering
must be checked after installing on the S24. Local virtual-HID injection was
unavailable to the build process, so absolute centering has not been verified
against the physical Mac/phone link.

The Mac helper can print pairing diagnostics:

```sh
dist/AirMouse.app/Contents/MacOS/AirMouse --diagnostics
```

## Files

- `android/app/src/main/java/dev/airmouse/` — phone UI, motion, IME bridge, HID.
- `mac/Sources/AirMouse/` — installer helper.
- `scripts/` — local builds and checks.
- `dist/AirMouse-Android.apk` — phone installer.
- `dist/AirMouse.app` — optional Mac installer helper.
- `dist/AirMouse-WiFi-source-backup.tar.gz` — preserved original Wi-Fi source.

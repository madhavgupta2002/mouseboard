# Development

## Components

`apps/android` is a native Java controller with no Gradle dependency. The build
uses pinned official Android SDK packages, compiles the sources with `javac`,
converts them with D8, and signs the APK with a local development key.

`apps/macos` is a Swift package. The build produces a SwiftUI companion, bundles
the Android APK, generates the icon, and ad-hoc signs the `.app`.

`apps/windows` contains the Windows companion and its own build instructions.
The Windows application has the same setup role as the Mac companion.

## Commands

| Command | Purpose |
| --- | --- |
| `bash scripts/build-android.sh` | Build `dist/MouseBoard-Android.apk` |
| `bash scripts/build-mac.sh` | Bundle the APK into `dist/MouseBoard.app` |
| `bash scripts/build-all.sh` | Build Android and macOS |
| `bash scripts/test.sh --unit-only` | Run platform-independent Java logic tests |
| `bash scripts/test.sh` | Run logic tests and verify built APK/Mac artifacts |
| `powershell -File scripts/build-windows.ps1` | Build/package the Windows companion |
| `python3 scripts/package-windows.py` | Build a standalone Windows EXE, also on macOS/Linux |
| `bash scripts/package-mac.sh` | Build the universal Mac app and drag-to-install DMG |
| `python3 scripts/prepare-downloads.py` | Copy all three finished packages into `downloads/` with hashes |
| `python3 scripts/verify-downloads.py` | Verify the committed download checksums and binary formats |

The macOS build needs Apple's Command Line Tools. Android builds currently use
the macOS SDK tools and need Java 17+ and Python 3.9+. `.tools` is a project-local SDK/cache directory, not a global
SDK installation.

## Signing and update compatibility

Do not commit local signing keys.

The development APK key remains `.tools/airmouse-debug.keystore`. Keep it to
update an existing local installation. A fresh clone generates a new key, which
cannot update an APK signed by a different developer's key.

The visible product and artifact names are MouseBoard. Android's `dev.airmouse`
application ID and macOS's `dev.airmouse.mac` bundle identifier intentionally
remain unchanged for installation compatibility.

## Bluetooth reports

MouseBoard registers a composite mouse/keyboard descriptor in report mode:

| Report ID | Input bytes | Purpose |
| --- | --- | --- |
| 1 | Buttons, signed X, signed Y, signed wheel | Relative mouse, drag and scroll |
| 2 | Modifier, reserved, six key usages | Keyboard |
| 3 | Unsigned 16-bit X and Y, range 0–32767 | Absolute centering |

The host can send keyboard LED state as a one-byte output report on ID 2.
Mouse button transitions and key-down/key-up pairs use bounded queues.
Pause/disconnect sends neutral mouse and keyboard reports.

Recenter sends two absolute positions: `(16383, 16383)`, then the exact midpoint
`(16384, 16384)`. macOS caches absolute HID values separately from relative
movement and can ignore an unchanged absolute report. Sending the midpoint
alone therefore worked once but could fail after moving the cursor again.
The one-unit lead-in changes both cached axes, so every press finishes with a
fresh center event. Version 0.4.1 changes only the report sequence, not the HID
descriptor; this update does not require a new Bluetooth pairing.

The regression test models unchanged-value filtering in Apple's
[`IOHIDElementPrivate::processReport`](https://github.com/apple-oss-distributions/IOHIDFamily/blob/main/IOHIDFamily/IOHIDElementPrivate.cpp)
and repeats the move/recenter cycle five times.

The original mouse-only edition used three bytes and no report ID. A host that
keeps that old descriptor while receiving a numbered mouse report reads the
button byte as X and horizontal movement as Y. This was observed in the affected
Mac's live IORegistry after the UI/keyboard upgrade. Re-pairing both devices
refreshes the descriptor. The Mac helper inspects the selected phone's active
HID profile and warns about the legacy format; regression checks reproduce the
byte shift and validate the current descriptor exported by the Android tests.

The keyboard bridge uses the phone's normal input method and applies suffix
edits for English composition and autocorrection. It does not read the
computer's text or inject commands through a desktop receiver.

Version 0.5.0 keeps the HID descriptor unchanged. Back/Forward enqueue balanced
keyboard shortcuts: Command + bracket on Mac, Alt + arrow on Windows.
Horizontal scrolling serializes Shift-down, a wheel report and Shift-up only
while the keyboard queue is idle, preserving held mouse buttons. It relies on
the receiving application's Shift-wheel behavior.

## Background and touch input

`MouseBoardService` owns the HID connection and rotation sensor. The activity
binds to it; activity pause/destruction releases touch gestures without closing
Bluetooth. A connected-device foreground notification exposes Pause/Resume
and Disconnect. A partial wake lock is held only for active tilt sensing and
released when paused, disconnected, destroyed or switched to touchpad mode.
The service is non-sticky: process death requires an explicit reconnection.

The manifest declares `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`,
`WAKE_LOCK` and `POST_NOTIFICATIONS` alongside the existing Bluetooth permissions.
It still requests no Internet permission. The service starts from the visible
activity after Bluetooth permission has been granted.

`TouchpadGestures` contains the Android-independent gesture state; `TouchpadView`
adapts motion events (including batched historical samples), centroid changes
and long-press timing. One finger moves the pointer with a 0.5 dp starting
threshold, independently of the larger tap/scroll slop. Tapping clicks; a second
touch within 300 ms can drag immediately, or a long press starts a drag.
Two fingers scroll or tap to right-click.

Scrolling velocity uses an exponential average and a 150 ms exponential decay
after release. New touches, cancellation, toolbar actions and suspended frames
stop momentum. Pointer speed and scroll direction are persistent settings.
Both scroll axes are retained; the HID wheel still has discrete steps, rather
than native trackpad pixel scrolling.

Two-finger centroid travel and span changes distinguish scrolling from pinch.
Once chosen, the gesture type stays fixed until release. A 15% pinch step
queues an app zoom shortcut; three-finger swipes send a desktop/window shortcut
once per gesture. These use the existing keyboard report, preserving pairing.
Pointer motion is suppressed throughout a multi-touch gesture, including after
one finger lifts. Cancellation and mode changes release drag buttons. The
selected mode persists; touchpad mode stops tilt sensing.

Dim mode uses the same surface with low requested window brightness and hidden
system bars. In button mode it maps the two halves to clicks and one-finger
swipes to scrolling. This is an awake display, not touch capture while locked.

## Motion

The default horizontal mapping uses left/right turning (relative yaw). Pitch
controls vertical movement. Version 0.4.0 migrates existing preferences to this
default once, then preserves subsequent explicit mode changes.

The game rotation vector supplies calibrated orientation; the standard rotation
vector is the fallback. Independent per-axis adaptive filters smooth motion near
rest and respond faster during a deliberate sweep. A small angular hysteresis
suppresses tiny reversals while allowing slow motion to accumulate.

Sensor gaps and discontinuities reset the filters rather than producing delayed
jumps. The approach follows Casiez, Roussel and Vogel's *1 € Filter*, with
angular wrapping and position hysteresis.

## Verification limits

The Java tests cover motion noise, slow aiming, fast sweeps, sample-rate
consistency, drag persistence, cancellation, scrolling, repeated recentering, report descriptors,
keyboard mapping, navigation shortcuts, queue bounds, IME suffix edits,
touchpad fine movement, tap-then-drag, multi-finger transitions, pinch/scroll
classification, momentum decay/cancellation, OS shortcuts and drag cancellation.

Full verification also runs Swift checks for stale HID profiles and report-ID
alignment. Reading the active profile does not capture keyboard or mouse input.

Artifact checks inspect the built APK's permissions/version and verify APK/Mac
signatures. They do not prove a physical HID connection, native phone layout,
default-keyboard behavior or the host's display mapping. Test those after
installing on a real Android phone and pairing it with each target OS.

Generated artifacts, SDKs, caches and machine-specific screenshots remain
ignored under `dist/` and `.tools/`. The three end-user packages in `downloads/`
are intentionally committed so users do not need to compile or sign in to
download workflow artifacts. Rebuild the platform packages, run
`prepare-downloads.py`, review the hashes, and commit the updated downloads
when publishing a new version. Keep each file below GitHub's 100 MiB limit.

GitHub Actions builds the Android installer and both desktop companions. The
Windows job packages the macOS job's APK, and the macOS job verifies its
bundled installer. CI generates a temporary development signing key on each
runner; those APKs do not replace an installation signed with your local key.

The Windows EXE embeds the APK as a manifest resource and bundles compressed
.NET runtime/native libraries. Runtime extraction needs only the user's local
app-data/temp directories. Portable tests read the produced assembly's resource
table without loading its architecture-specific code and compare the embedded
APK byte-for-byte with the Android build.

The Mac executable contains both arm64 and x86_64 slices targeting macOS 14.
The DMG includes the app and an Applications shortcut. Local builds are ad-hoc
signed, not Apple-notarized; Windows builds are not Authenticode-signed.
